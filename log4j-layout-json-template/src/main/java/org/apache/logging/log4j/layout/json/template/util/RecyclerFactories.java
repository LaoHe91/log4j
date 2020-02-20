/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.layout.json.template.util;

import org.apache.logging.log4j.core.util.Constants;
import org.apache.logging.log4j.plugins.Plugin;
import org.apache.logging.log4j.plugins.convert.TypeConverter;
import org.apache.logging.log4j.plugins.convert.TypeConverters;
import org.apache.logging.log4j.util.LoaderUtil;
import org.apache.logging.log4j.util.Strings;
import org.jctools.queues.MpmcArrayQueue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Supplier;

public enum RecyclerFactories {;

    private static final String JCTOOLS_QUEUE_CLASS_SUPPLIER_PATH =
            "org.jctools.queues.MpmcArrayQueue.new";

    private static final boolean JCTOOLS_QUEUE_CLASS_AVAILABLE =
            isJctoolsQueueClassAvailable();

    private static boolean isJctoolsQueueClassAvailable() {
        try {
            final String className = JCTOOLS_QUEUE_CLASS_SUPPLIER_PATH
                    .replaceAll("\\.new$", "");
            LoaderUtil.loadClass(className);
            return true;
        } catch (final ClassNotFoundException ignored) {
            return false;
        }
    }

    @Plugin(name = "RecyclerFactory", category = TypeConverters.CATEGORY)
    public static final class RecyclerFactoryConverter implements TypeConverter<RecyclerFactory> {
        @Override
        public RecyclerFactory convert(final String recyclerFactorySpec) {
            return ofSpec(recyclerFactorySpec);
        }
    }

    public static RecyclerFactory ofSpec(final String recyclerFactorySpec) {

        // Determine the default capacity.
        int defaultCapacity = Math.max(
                2 * Runtime.getRuntime().availableProcessors() + 1,
                8);

        // TLA-, MPMC-, or ABQ-based queueing factory -- if nothing is specified.
        if (recyclerFactorySpec == null) {
            if (Constants.ENABLE_THREADLOCALS) {
                return ThreadLocalRecyclerFactory.getInstance();
            } else {
                final Supplier<Queue<Object>> queueSupplier =
                        JCTOOLS_QUEUE_CLASS_AVAILABLE
                                ? () -> new MpmcArrayQueue<>(defaultCapacity)
                                : () -> new ArrayBlockingQueue<>(defaultCapacity);
                return new QueueingRecyclerFactory(queueSupplier);
            }
        }

        // Is a dummy factory requested?
        else if (recyclerFactorySpec.equals("dummy")) {
            return DummyRecyclerFactory.getInstance();
        }

        // Is a TLA factory requested?
        else if (recyclerFactorySpec.equals("threadLocal")) {
            return ThreadLocalRecyclerFactory.getInstance();
        }

        // Is a queueing factory requested?
        else if (recyclerFactorySpec.startsWith("queue")) {
            return readQueueingRecyclerFactory(recyclerFactorySpec, defaultCapacity);
        }

        // Bogus input, bail out.
        else {
            throw new IllegalArgumentException(
                    "invalid recycler factory: " + recyclerFactorySpec);
        }

    }

    private static RecyclerFactory readQueueingRecyclerFactory(
            final String recyclerFactorySpec,
            final int defaultCapacity) {

        // Set defaults.
        String supplierPath = JCTOOLS_QUEUE_CLASS_AVAILABLE
                ? JCTOOLS_QUEUE_CLASS_SUPPLIER_PATH
                : "java.util.concurrent.ArrayBlockingQueue.new";
        boolean supplierPathProvided = false;
        int capacity = defaultCapacity;
        boolean capacityProvided = false;
        final String queueFactorySpec = recyclerFactorySpec.substring(
                "queue".length() +
                        (recyclerFactorySpec.startsWith("queue:")
                                ? 1
                                : 0));

        // Read the user-provided spec.
        final String[] queueFactorySpecFields = queueFactorySpec.split("\\s*,\\s*", 2);
        for (final String queueFactorySpecField : queueFactorySpecFields) {

            // Skip blank fields.
            if (Strings.isBlank(queueFactorySpecField)) {
                continue;
            }

            // Split the key-value pair.
            final String[] keyAndValue = queueFactorySpecField.split("\\s*=\\s*", 2);
            if (keyAndValue.length != 2) {
                throw new IllegalArgumentException(
                        "invalid queueing recycler: " + recyclerFactorySpec);
            }
            final String key = keyAndValue[0];
            final String value = keyAndValue[1];

            switch (key) {

                // Read supplier path.
                case "supplier": {
                    if (supplierPathProvided) {
                        throw new IllegalArgumentException(
                                "multiple occurrences of supplier in queueing " +
                                        "recycler factory: " + queueFactorySpec);
                    }
                    supplierPathProvided = true;
                    supplierPath = value;
                    break;
                }

                // Read capacity.
                case "capacity": {
                    if (capacityProvided) {
                        throw new IllegalArgumentException(
                                "multiple occurrences of capacity in queueing " +
                                        "recycler factory: " + queueFactorySpec);
                    }
                    capacityProvided = true;
                    try {
                        capacity = Integer.parseInt(value);
                    } catch (NumberFormatException error) {
                        throw new IllegalArgumentException(
                                "failed reading capacity in queueing recycler " +
                                        "factory: " + queueFactorySpec, error);
                    }
                    break;
                }

                // Bogus input.
                default:
                    throw new IllegalArgumentException(
                            "invalid queueing recycler factory: " +
                                    queueFactorySpec);

            }
        }

        // Execute the read spec.
        return createRecyclerFactory(queueFactorySpec, supplierPath, capacity);

    }

    private static RecyclerFactory createRecyclerFactory(
            final String queueFactorySpec,
            final String supplierPath,
            final int capacity) {
        final int supplierPathSplitterIndex = supplierPath.lastIndexOf('.');
        if (supplierPathSplitterIndex < 0) {
            throw new IllegalArgumentException(
                    "invalid supplier in queueing recycler factory: " +
                            queueFactorySpec);
        }
        final String supplierClassName = supplierPath.substring(0, supplierPathSplitterIndex);
        final String supplierMethodName = supplierPath.substring(supplierPathSplitterIndex + 1);
        try {
            final Class<?> supplierClass = LoaderUtil.loadClass(supplierClassName);
            final Supplier<Queue<Object>> queueSupplier;
            if ("new".equals(supplierMethodName)) {
                final Constructor<?> supplierCtor =
                        supplierClass.getDeclaredConstructor(int.class);
                queueSupplier = () -> {
                    try {
                        @SuppressWarnings("unchecked")
                        final Queue<Object> typedQueue =
                                (Queue<Object>) supplierCtor.newInstance(capacity);
                        return typedQueue;
                    } catch (final Exception error) {
                        throw new RuntimeException(
                                "recycler queue construction failed for factory: " +
                                        queueFactorySpec, error);
                    }
                };
            } else {
                final Method supplierMethod =
                        supplierClass.getMethod(supplierMethodName, int.class);
                queueSupplier = () -> {
                    try {
                        @SuppressWarnings("unchecked")
                        final Queue<Object> typedQueue =
                                (Queue<Object>) supplierMethod.invoke(null, capacity);
                        return typedQueue;
                    } catch (final Exception error) {
                        throw new RuntimeException(
                                "recycler queue construction failed for factory: " +
                                        queueFactorySpec, error);
                    }
                };
            }
            return new QueueingRecyclerFactory(queueSupplier);
        } catch (final Exception error) {
            throw new RuntimeException(
                    "failed executing queueing recycler factory: " +
                            queueFactorySpec, error);
        }
    }

}
