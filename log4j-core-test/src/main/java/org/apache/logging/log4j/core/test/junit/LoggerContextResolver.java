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
package org.apache.logging.log4j.core.test.junit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.LoggerContextAccessor;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.core.selector.ContextSelector;
import org.apache.logging.log4j.core.util.NetUtils;
import org.apache.logging.log4j.plugins.di.Binding;
import org.apache.logging.log4j.plugins.di.ConfigurableInstanceFactory;
import org.apache.logging.log4j.plugins.di.DI;
import org.apache.logging.log4j.test.junit.TypeBasedParameterResolver;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContextException;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.platform.commons.support.AnnotationSupport;

class LoggerContextResolver extends TypeBasedParameterResolver<LoggerContext>
        implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback {
    private static final String FQCN = LoggerContextResolver.class.getName();
    private static final Namespace BASE_NAMESPACE = Namespace.create(LoggerContext.class);

    public LoggerContextResolver() {
        super(LoggerContext.class);
    }

    @Override
    public void beforeAll(final ExtensionContext context) throws Exception {
        final Class<?> testClass = context.getRequiredTestClass();
        AnnotationSupport.findAnnotation(testClass, LoggerContextSource.class)
                .ifPresent(testSource -> setUpLoggerContext(testSource, context));
    }

    @Override
    public void beforeEach(final ExtensionContext context) throws Exception {
        final Class<?> testClass = context.getRequiredTestClass();
        if (AnnotationSupport.isAnnotated(testClass, LoggerContextSource.class)) {
            final Store testClassStore = context.getStore(BASE_NAMESPACE.append(testClass));
            final LoggerContextAccessor accessor =
                    testClassStore.get(LoggerContextAccessor.class, LoggerContextAccessor.class);
            if (accessor == null) {
                throw new IllegalStateException(
                        "Specified @LoggerContextSource but no LoggerContext found for test class "
                                + testClass.getCanonicalName());
            }
            if (testClassStore.get(ReconfigurationPolicy.class, ReconfigurationPolicy.class)
                    == ReconfigurationPolicy.BEFORE_EACH) {
                accessor.getLoggerContext().reconfigure();
            }
        }
        AnnotationSupport.findAnnotation(context.getRequiredTestMethod(), LoggerContextSource.class)
                .ifPresent(source -> {
                    final LoggerContext loggerContext = setUpLoggerContext(source, context);
                    if (source.reconfigure() == ReconfigurationPolicy.BEFORE_EACH) {
                        loggerContext.reconfigure();
                    }
                });
    }

    @Override
    public void afterEach(final ExtensionContext context) throws Exception {
        final Class<?> testClass = context.getRequiredTestClass();
        if (AnnotationSupport.isAnnotated(testClass, LoggerContextSource.class)) {
            final Store testClassStore = getTestStore(context);
            if (testClassStore.get(ReconfigurationPolicy.class, ReconfigurationPolicy.class)
                    == ReconfigurationPolicy.AFTER_EACH) {
                testClassStore
                        .get(LoggerContextAccessor.class, LoggerContextAccessor.class)
                        .getLoggerContext()
                        .reconfigure();
            }
        }
    }

    @Override
    public LoggerContext resolveParameter(
            final ParameterContext parameterContext, final ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return getLoggerContext(extensionContext);
    }

    static LoggerContext getLoggerContext(final ExtensionContext context) {
        final Store store = getTestStore(context);
        final LoggerContextAccessor accessor = store.get(LoggerContextAccessor.class, LoggerContextAccessor.class);
        assertNotNull(accessor);
        return accessor.getLoggerContext();
    }

    private static Store getTestStore(final ExtensionContext context) {
        return context.getStore(BASE_NAMESPACE.append(context.getRequiredTestClass()));
    }

    private static LoggerContext setUpLoggerContext(
            final LoggerContextSource source, final ExtensionContext extensionContext) {
        final String displayName = extensionContext.getDisplayName();
        final ConfigurableInstanceFactory instanceFactory = DI.createFactory();
        extensionContext.getTestInstance().ifPresent(instanceFactory::registerBundle);
        final Class<? extends ContextSelector> contextSelectorClass = source.selector();
        if (contextSelectorClass != ContextSelector.class) {
            instanceFactory.registerBinding(
                    Binding.from(ContextSelector.KEY).to(instanceFactory.getFactory(contextSelectorClass)));
        }
        DI.initializeFactory(instanceFactory);
        final Log4jContextFactory loggerContextFactory;
        if (source.bootstrap()) {
            loggerContextFactory = new Log4jContextFactory(instanceFactory);
            LogManager.setFactory(loggerContextFactory);
        } else {
            loggerContextFactory = (Log4jContextFactory) LogManager.getFactory();
        }
        final Class<?> testClass = extensionContext.getRequiredTestClass();
        final ClassLoader classLoader = testClass.getClassLoader();
        final Map.Entry<String, Object> injectorContext =
                Map.entry(ConfigurableInstanceFactory.class.getName(), instanceFactory);
        final String configLocation = getConfigLocation(source, extensionContext);
        final URI configUri;
        if (source.v1config()) {
            System.setProperty(ConfigurationFactory.LOG4J1_CONFIGURATION_FILE_PROPERTY.getSystemKey(), configLocation);
            configUri = null; // handled by system property
        } else {
            configUri = NetUtils.toURI(configLocation);
        }
        final LoggerContext context =
                loggerContextFactory.getContext(FQCN, classLoader, injectorContext, false, configUri, displayName);
        assertNotNull(
                context, () -> "No LoggerContext created for " + testClass + " and config file " + configLocation);
        final Store store = getTestStore(extensionContext);
        store.put(ReconfigurationPolicy.class, source.reconfigure());
        store.put(LoggerContextAccessor.class, new ContextHolder(context, source.timeout(), source.unit()));
        return context;
    }

    private static String getConfigLocation(final LoggerContextSource source, final ExtensionContext extensionContext) {
        final String value = source.value();
        if (value.isEmpty()) {
            Class<?> clazz = extensionContext.getRequiredTestClass();
            while (clazz != null) {
                final URL url = clazz.getResource(clazz.getSimpleName() + ".xml");
                if (url != null) {
                    try {
                        return url.toURI().toString();
                    } catch (URISyntaxException e) {
                        throw new ExtensionContextException("An error occurred accessing the configuration.", e);
                    }
                }
                clazz = clazz.getSuperclass();
            }
            return extensionContext.getRequiredTestClass().getName().replaceAll("[.$]", "/") + ".xml";
        }
        return value;
    }

    private static final class ContextHolder implements Store.CloseableResource, LoggerContextAccessor {
        private final LoggerContext context;
        private final long shutdownTimeout;
        private final TimeUnit unit;

        private ContextHolder(final LoggerContext context, final long shutdownTimeout, final TimeUnit unit) {
            this.context = context;
            this.shutdownTimeout = shutdownTimeout;
            this.unit = unit;
        }

        @Override
        public LoggerContext getLoggerContext() {
            return context;
        }

        @Override
        public void close() throws Throwable {
            try {
                context.stop(shutdownTimeout, unit);
            } finally {
                System.clearProperty(ConfigurationFactory.LOG4J1_EXPERIMENTAL.getSystemKey());
                System.clearProperty(ConfigurationFactory.LOG4J1_CONFIGURATION_FILE_PROPERTY.getSystemKey());
            }
        }
    }
}
