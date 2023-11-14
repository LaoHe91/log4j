/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.logging.log4j.plugins.di;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.plugins.FactoryType;
import org.apache.logging.log4j.plugins.ScopeType;
import org.apache.logging.log4j.plugins.condition.Condition;
import org.apache.logging.log4j.plugins.condition.ConditionContext;
import org.apache.logging.log4j.plugins.condition.Conditional;
import org.apache.logging.log4j.plugins.di.spi.DependencyChain;
import org.apache.logging.log4j.plugins.di.spi.FactoryResolver;
import org.apache.logging.log4j.plugins.di.spi.InjectionPoint;
import org.apache.logging.log4j.plugins.di.spi.InstancePostProcessor;
import org.apache.logging.log4j.plugins.di.spi.ReflectionAgent;
import org.apache.logging.log4j.plugins.di.spi.ResolvableKey;
import org.apache.logging.log4j.plugins.di.spi.Scope;
import org.apache.logging.log4j.plugins.internal.util.BeanUtils;
import org.apache.logging.log4j.plugins.internal.util.BindingMap;
import org.apache.logging.log4j.plugins.internal.util.HierarchicalMap;
import org.apache.logging.log4j.plugins.util.AnnotationUtil;
import org.apache.logging.log4j.plugins.util.OrderedComparator;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Cast;
import org.apache.logging.log4j.util.LoaderUtil;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.apache.logging.log4j.util.PropertyEnvironment;

public class DefaultInstanceFactory implements ConfigurableInstanceFactory {

    private static final Logger LOGGER = StatusLogger.getLogger();

    private final ThreadLocal<InjectionPoint<?>> currentInjectionPoint = new ThreadLocal<>();

    private final BindingMap bindings;
    private final HierarchicalMap<Class<? extends Annotation>, Scope> scopes;
    private final List<FactoryResolver<?>> factoryResolvers;
    private final SortedSet<InstancePostProcessor> instancePostProcessors = new ConcurrentSkipListSet<>(
            Comparator.comparing(InstancePostProcessor::getClass, OrderedComparator.INSTANCE));
    private ReflectionAgent agent = object -> object.setAccessible(true);

    protected DefaultInstanceFactory() {
        this(BindingMap.newRootMap(), HierarchicalMap.newRootMap(), new ArrayList<>(), List.of());
    }

    protected DefaultInstanceFactory(final DefaultInstanceFactory parent) {
        this(
                parent.bindings.newChildMap(),
                parent.scopes.newChildMap(),
                parent.factoryResolvers,
                parent.instancePostProcessors
        );
        this.agent = parent.agent;
    }

    private DefaultInstanceFactory(final BindingMap bindings,
                                   final HierarchicalMap<Class<? extends Annotation>, Scope> scopes,
                                   final List<FactoryResolver<?>> factoryResolvers,
                                   final Collection<InstancePostProcessor> instancePostProcessors) {
        this.bindings = bindings;
        this.scopes = scopes;
        this.factoryResolvers = factoryResolvers;
        this.instancePostProcessors.addAll(instancePostProcessors);
        this.bindings.put(Binding.from(InjectionPoint.CURRENT_INJECTION_POINT).to(currentInjectionPoint::get));
        this.bindings.put(Binding.from(ConfigurableInstanceFactory.class).toInstance(this));
        this.bindings.put(Binding.from(InstanceFactory.class).toInstance(this));
        this.bindings.put(Binding.from(PropertyEnvironment.class).to(PropertiesUtil::getProperties));
        this.bindings.put(Binding.from(ClassLoader.class).to(LoaderUtil::getClassLoader));
    }

    @Override
    public <T> Supplier<T> getFactory(final ResolvableKey<T> resolvableKey) {
        final Key<T> key = resolvableKey.getKey();
        final Binding<T> existingBinding = bindings.get(key, resolvableKey.getAliases());
        if (existingBinding != null) {
            return existingBinding;
        }
        final Supplier<T> unscoped = resolveKey(resolvableKey)
                .orElseGet(() -> createDefaultFactory(resolvableKey));
        final Scope scope = getScopeForType(key.getRawType());
        final Supplier<T> scoped = scope.get(key, unscoped);
        final Binding<T> binding = Binding.from(key).to(scoped);
        registerBinding(binding);
        return binding;
    }

    protected <T> Optional<Supplier<T>> resolveKey(final ResolvableKey<T> resolvableKey) {
        return factoryResolvers.stream()
                .filter(resolver -> resolver.supportsKey(resolvableKey.getKey()))
                .findFirst()
                .map(Cast::<FactoryResolver<T>>cast)
                .map(resolver -> resolver.getFactory(resolvableKey, this));
    }

    protected <T> Supplier<T> createDefaultFactory(final ResolvableKey<T> resolvableKey) {
        final Key<T> key = resolvableKey.getKey();
        if (key.getQualifierType() != null) {
            // TODO(ms): would be useful to provide some logs about possible matches
            throw new NoQualifiedBindingException(resolvableKey);
        }
        if (!BeanUtils.isInjectable(key.getRawType())) {
            throw new NotInjectableException(resolvableKey);
        }
        return () -> {
            var instance = getInjectableInstance(resolvableKey);
            instance = postProcessBeforeInitialization(resolvableKey, instance);
            injectMembers(key, instance, resolvableKey.getDependencyChain());
            instance = postProcessAfterInitialization(resolvableKey, instance);
            if (instance instanceof Supplier<?>) {
                final Supplier<T> supplier = Cast.cast(instance);
                instance = supplier.get();
            }
            return instance;
        };
    }

    protected <T> T getInjectableInstance(final ResolvableKey<T> resolvableKey) {
        final Class<T> rawType = resolvableKey.getRawType();
        validate(rawType, resolvableKey.getName(), rawType);
        final Executable factory = BeanUtils.getInjectableFactory(resolvableKey);
        final Key<T> key = resolvableKey.getKey();
        final DependencyChain updatedChain = resolvableKey.getDependencyChain()
                .withDependency(key);
        final Object[] arguments = InjectionPoint.fromExecutable(factory)
                .stream()
                .map(point -> getArgumentFactory(point, updatedChain).get())
                .toArray();
        return invokeFactory(factory, arguments);
    }

    protected <T> T postProcessBeforeInitialization(final ResolvableKey<T> resolvableKey, final T instance) {
        var value = instance;
        for (final InstancePostProcessor instancePostProcessor : instancePostProcessors) {
            value = instancePostProcessor.postProcessBeforeInitialization(resolvableKey, value);
        }
        return value;
    }

    protected <T> T postProcessAfterInitialization(final ResolvableKey<T> resolvableKey, final T instance) {
        var value = instance;
        for (final InstancePostProcessor instancePostProcessor : instancePostProcessors) {
            value = instancePostProcessor.postProcessAfterInitialization(resolvableKey, value);
        }
        return value;
    }

    protected <T> T invokeFactory(final Executable factory, final Object... arguments) {
        if (factory instanceof Method) {
            return Cast.cast(agent.invokeMethod((Method) factory, null, arguments));
        } else {
            return agent.newInstance(Cast.cast(factory), arguments);
        }
    }

    protected List<Supplier<?>> getArgumentFactories(final Key<?> key,
                                                     final List<InjectionPoint<?>> argumentInjectionPoints,
                                                     final DependencyChain dependencyChain) {
        final DependencyChain newChain = dependencyChain.withDependency(key);
        return argumentInjectionPoints.stream()
                .map(injectionPoint -> getArgumentFactory(injectionPoint, newChain))
                .collect(Collectors.toList());
    }

    protected <T> Supplier<T> getArgumentFactory(
            final InjectionPoint<T> injectionPoint, final DependencyChain dependencyChain) {
        final Key<T> key = injectionPoint.getKey();
        if (key.getRawType() != Supplier.class && dependencyChain.hasDependency(key)) {
            throw new CircularDependencyException(key, dependencyChain);
        }
        final AnnotatedElement element = injectionPoint.getElement();
        final ResolvableKey<T> resolvableKey = ResolvableKey.of(key, injectionPoint.getAliases(), dependencyChain);
        return () -> {
            currentInjectionPoint.set(injectionPoint);
            try {
                final T instance = getInstance(resolvableKey);
                validate(element, key.getName(), instance);
                return instance;
            } finally {
                currentInjectionPoint.remove();
            }
        };
    }

    @Override
    public boolean hasBinding(final Key<?> key) {
        return bindings.containsKey(key);
    }

    @Override
    public Scope getRegisteredScope(final Class<? extends Annotation> scopeType) {
        return scopes.get(scopeType);
    }

    @Override
    public void registerScope(final Class<? extends Annotation> scopeType, final Scope scope) {
        scopes.put(scopeType, scope);
    }

    protected Scope getScopeForType(final Class<?> type) {
        final Annotation scopeType = AnnotationUtil.getElementAnnotationHavingMetaAnnotation(type, ScopeType.class);
        final Scope registeredScope = scopeType != null ? getRegisteredScope(scopeType.annotationType()) : null;
        return registeredScope != null ? registeredScope : DefaultScope.INSTANCE;
    }

    protected Scope getScopeForMethod(final Method method) {
        final Annotation methodScopeType = AnnotationUtil.getElementAnnotationHavingMetaAnnotation(method, ScopeType.class);
        final Scope methodScope = methodScopeType != null ? getRegisteredScope(methodScopeType.annotationType()) : null;
        return methodScope != null ? methodScope : getScopeForType(method.getReturnType());
    }

    @Override
    public void registerBundle(final Object bundle) {
        final Object bundleInstance = bundle instanceof Class<?> ? getInstance((Class<?>) bundle) : bundle;
        final Class<?> bundleClass = bundleInstance.getClass();
        final Set<? extends Class<? extends Condition>> conditionalClasses =
                AnnotationUtil.findLogicalAnnotations(bundleClass, Conditional.class)
                        .map(Conditional::value)
                        .collect(Collectors.toSet());
        final List<? extends Condition> globalConditions = conditionalClasses.stream()
                .map(this::getInstance)
                .collect(Collectors.toList());
        final ConditionContext context = ConditionContext.of(this);
        final List<Method> factoryMethods = new ArrayList<>();
        for (final Method method : AnnotationUtil.getDeclaredMethodsMetaAnnotatedWith(bundleClass, FactoryType.class)) {
            if (bundleClass.equals(method.getDeclaringClass()) || factoryMethods.stream().noneMatch(m ->
                    method.getName().equals(m.getName()) && Arrays.equals(method.getParameterTypes(), m.getParameterTypes()))) {
                final Stream<? extends Condition> localConditions =
                        AnnotationUtil.findLogicalAnnotations(method, Conditional.class)
                                .map(Conditional::value)
                                .filter(conditionalClass -> !conditionalClasses.contains(conditionalClass))
                                .map(this::getInstance);
                final Stream<Condition> applicableConditions = Stream.concat(globalConditions.stream(), localConditions);
                if (applicableConditions.allMatch(condition -> condition.matches(context, method))) {
                    LOGGER.debug("Registering binding for bundle ({}) method: {}", bundleClass, method);
                    registerBundleMethod(bundleInstance, method);
                    factoryMethods.add(method);
                }
            }
        }
    }

    protected <T> void registerBundleMethod(final Object bundleInstance, final Method method) {
        final Key<T> primaryKey = Key.forMethod(method);
        if (hasBinding(primaryKey)) {
            LOGGER.error("Binding already exists for {}", primaryKey);
            throw new DuplicateBindingException(primaryKey);
        }
        final List<InjectionPoint<?>> injectionPoints = InjectionPoint.fromExecutable(method);
        final List<Supplier<?>> argumentFactories =
                getArgumentFactories(primaryKey, injectionPoints, DependencyChain.empty());
        final ResolvableKey<T> resolvableKey = ResolvableKey.of(primaryKey);
        final Supplier<T> unscoped = () -> {
            final Object[] arguments = argumentFactories.stream()
                    .map(Supplier::get)
                    .toArray();
            T instance = Cast.cast(agent.invokeMethod(method, bundleInstance, arguments));
            instance = postProcessBeforeInitialization(resolvableKey, instance);
            injectMembers(primaryKey, instance, DependencyChain.empty());
            return postProcessAfterInitialization(resolvableKey, instance);
        };
        final Supplier<T> scoped = getScopeForMethod(method).get(primaryKey, unscoped);
        registerBinding(Binding.from(primaryKey).to(scoped));
        for (final String alias : Keys.getAliases(method)) {
            final Key<T> aliasKey = primaryKey.withName(alias);
            if (!hasBinding(aliasKey)) {
                registerBinding(Binding.from(aliasKey).to(scoped));
            }
        }
    }

    @Override
    public void registerBinding(final Binding<?> binding) {
        bindings.put(binding);
    }

    @Override
    public void registerBindingIfAbsent(final Binding<?> binding) {
        bindings.putIfAbsent(binding);
    }

    @Override
    public void removeBinding(final Key<?> key) {
        bindings.remove(key);
    }

    @Override
    public void registerFactoryResolver(final FactoryResolver<?> resolver) {
        factoryResolvers.add(resolver);
    }

    @Override
    public void registerInstancePostProcessor(final InstancePostProcessor instancePostProcessor) {
        instancePostProcessors.add(instancePostProcessor);
    }

    @Override
    public ConfigurableInstanceFactory newChildInstanceFactory() {
        return new DefaultInstanceFactory(this);
    }

    @Override
    public void setReflectionAgent(final ReflectionAgent accessor) {
        this.agent = accessor;
    }

    @Override
    public void injectMembers(final Object instance) {
        injectMembers(Key.forClass(instance.getClass()), instance, DependencyChain.empty());
    }

    protected void injectMembers(final Key<?> key, final Object instance, final DependencyChain dependencyChain) {
        final Class<?> rawType = instance.getClass();
        // first, inject fields and validate them
        for (final Field field : BeanUtils.getInjectableFields(rawType)) {
            injectField(field, instance);
        }
        // track the no-arg inject methods to execute later
        final List<Method> injectMethodsWithNoArgs = new ArrayList<>();
        // next, inject methods with args
        final DependencyChain updatedChain = dependencyChain.withDependency(key);
        for (final Method method : BeanUtils.getInjectableMethods(rawType)) {
            if (method.getParameterCount() == 0) {
                injectMethodsWithNoArgs.add(method);
            } else {
                final List<InjectionPoint<?>> injectionPoints = InjectionPoint.fromExecutable(method);
                final Object[] args = injectionPoints.stream()
                        .map(point -> getArgumentFactory(point, updatedChain))
                        .map(Supplier::get)
                        .toArray();
                agent.invokeMethod(method, instance, args);
            }
        }
        // now run the no-arg inject methods for post-initialization
        injectMethodsWithNoArgs.forEach(method -> agent.invokeMethod(method, instance));
    }

    protected <T> void injectField(final Field field, final Object instance) {
        final InjectionPoint<T> point = InjectionPoint.forField(field);
        currentInjectionPoint.set(point);
        final ResolvableKey<T> resolvableKey = ResolvableKey.of(point.getKey(), point.getAliases());
        try {
            final T value = getInstance(resolvableKey);
            if (value != null) {
                agent.setFieldValue(field, instance, value);
            }
            validate(field, resolvableKey.getName(), agent.getFieldValue(field, instance));
        } finally {
            currentInjectionPoint.remove();
        }
    }

    enum DefaultScope implements Scope {
        INSTANCE;

        @Override
        public <T> Supplier<T> get(final Key<T> key, final Supplier<T> unscoped) {
            return unscoped;
        }

        @Override
        public String toString() {
            return "[Unscoped]";
        }
    }
}
