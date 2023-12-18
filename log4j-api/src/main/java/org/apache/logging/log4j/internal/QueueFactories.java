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
package org.apache.logging.log4j.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Supplier;
import org.apache.logging.log4j.spi.QueueFactory;
import org.apache.logging.log4j.util.Cast;
import org.apache.logging.log4j.util.InternalApi;
import org.apache.logging.log4j.util.LoaderUtil;
import org.jctools.queues.MpmcArrayQueue;

/**
 * Provides {@link QueueFactory} instances for different use cases.
 * <p>
 * Implementations provided by <a href="https://jctools.github.io/JCTools/">JCTools</a> will be preferred, if available at runtime.
 * Otherwise, {@link ArrayBlockingQueue} will be used.
 * </p>
 *
 * @since 3.0.0
 */
@InternalApi
public enum QueueFactories implements QueueFactory {

    /**
     * Provides a bounded queue for multi-producer/multi-consumer usage.
     */
    MPMC(() -> MpmcArrayQueue::new);

    private final QueueFactory queueFactory;

    QueueFactories(final Supplier<QueueFactory> queueFactoryProvider) {
        this.queueFactory = getOrReplaceQueueFactory(queueFactoryProvider);
    }

    private static QueueFactory getOrReplaceQueueFactory(final Supplier<QueueFactory> queueFactoryProvider) {
        try {
            final QueueFactory queueFactory = queueFactoryProvider.get();
            // Test with a large enough capacity to avoid any `IllegalArgumentExceptions` from trivial queues
            queueFactory.create(16);
            return queueFactory;
        } catch (final LinkageError ignored) {
            return ArrayBlockingQueueFactory.INSTANCE;
        }
    }

    @Override
    public <E> Queue<E> create(final int capacity) {
        return queueFactory.create(capacity);
    }

    private static final class ArrayBlockingQueueFactory implements QueueFactory {

        private static final ArrayBlockingQueueFactory INSTANCE = new ArrayBlockingQueueFactory();

        private ArrayBlockingQueueFactory() {}

        @Override
        public <E> Queue<E> create(final int capacity) {
            return new ArrayBlockingQueue<>(capacity);
        }
    }

    /**
     * Creates a {@link QueueFactory} using the provided supplier.
     * <p>
     * A supplier path must be formatted as follows:
     * <ul>
     * <li>{@code <fully-qualified-class-name>.new} – the class constructor accepting a single {@code int} argument (denoting the capacity) will be used (e.g., {@code org.jctools.queues.MpmcArrayQueue.new})</li>
     * <li>{@code <fully-qualified-class-name>.<static-factory-method>} – the static factory method accepting a single {@code int} argument (denoting the capacity) will be used (e.g., {@code com.acme.Queues.createBoundedQueue})</li>
     * </ul>
     * </p>
     *
     * @param supplierPath a queue supplier path (e.g., {@code org.jctools.queues.MpmcArrayQueue.new}, {@code com.acme.Queues.createBoundedQueue})
     * @return a new {@link QueueFactory} instance
     */
    public static QueueFactory ofSupplier(final String supplierPath) {
        final int supplierPathSplitterIndex = supplierPath.lastIndexOf('.');
        if (supplierPathSplitterIndex < 0) {
            final String message = String.format("invalid queue factory supplier path: `%s`", supplierPath);
            throw new IllegalArgumentException(message);
        }
        final String supplierClassName = supplierPath.substring(0, supplierPathSplitterIndex);
        final String supplierMethodName = supplierPath.substring(supplierPathSplitterIndex + 1);
        try {
            final Class<?> supplierClass = LoaderUtil.loadClass(supplierClassName);
            if ("new".equals(supplierMethodName)) {
                final Constructor<?> supplierCtor = supplierClass.getDeclaredConstructor(int.class);
                return new ConstructorProvidedQueueFactory(supplierCtor);
            } else {
                final Method supplierMethod = supplierClass.getMethod(supplierMethodName, int.class);
                return new StaticMethodProvidedQueueFactory(supplierMethod);
            }
        } catch (final ReflectiveOperationException | LinkageError | SecurityException error) {
            final String message =
                    String.format("failed to create the queue factory using the supplier path `%s`", supplierPath);
            throw new RuntimeException(message, error);
        }
    }

    private static final class ConstructorProvidedQueueFactory implements QueueFactory {

        private final Constructor<?> constructor;

        private ConstructorProvidedQueueFactory(final Constructor<?> constructor) {
            this.constructor = constructor;
        }

        @Override
        public <E> Queue<E> create(final int capacity) {
            final Constructor<Queue<E>> typedConstructor = Cast.cast(constructor);
            try {
                return typedConstructor.newInstance(capacity);
            } catch (final ReflectiveOperationException error) {
                throw new RuntimeException("queue construction failure", error);
            }
        }
    }

    private static final class StaticMethodProvidedQueueFactory implements QueueFactory {

        private final Method method;

        private StaticMethodProvidedQueueFactory(final Method method) {
            this.method = method;
        }

        @Override
        public <E> Queue<E> create(final int capacity) {
            try {
                return Cast.cast(method.invoke(null, capacity));
            } catch (final ReflectiveOperationException error) {
                throw new RuntimeException("queue construction failure", error);
            }
        }
    }
}
