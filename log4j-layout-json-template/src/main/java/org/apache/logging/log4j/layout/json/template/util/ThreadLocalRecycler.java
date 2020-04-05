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

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ThreadLocalRecycler<V> implements Recycler<V> {

    private final Consumer<V> cleaner;

    private final ThreadLocal<V> holder;

    public ThreadLocalRecycler(
            final Supplier<V> supplier,
            final Consumer<V> cleaner) {
        this.cleaner = cleaner;
        this.holder = ThreadLocal.withInitial(supplier);
    }

    @Override
    public V acquire() {
        final V value = holder.get();
        cleaner.accept(value);
        return value;
    }

    @Override
    public void release(final V value) {}

}
