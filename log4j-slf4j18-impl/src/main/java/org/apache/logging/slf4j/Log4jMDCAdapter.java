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
package org.apache.logging.slf4j;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.ThreadContext;
import org.slf4j.spi.MDCAdapter;

/**
 *
 */
public class Log4jMDCAdapter implements MDCAdapter {

    private final ThreadLocalMapOfStacks threadLocalMapOfDeques = new ThreadLocalMapOfStacks();

    @Override
    public void put(final String key, final String val) {
        ThreadContext.put(key, val);
    }

    @Override
    public String get(final String key) {
        return ThreadContext.get(key);
    }

    @Override
    public void remove(final String key) {
        ThreadContext.remove(key);
    }

    @Override
    public void clear() {
        ThreadContext.clearMap();
    }

    @Override
    public Map<String, String> getCopyOfContextMap() {
        return ThreadContext.getContext();
    }

    @Override
    public void setContextMap(final Map<String, String> map) {
        ThreadContext.clearMap();
        ThreadContext.putAll(map);
    }

    @Override
    public void pushByKey(String key, String value) {
        threadLocalMapOfDeques.pushByKey(key, value);
    }

    @Override
    public String popByKey(String key) {
        return threadLocalMapOfDeques.popByKey(key);
    }

    @Override
    public Deque<String> getCopyOfDequeByKey(String key) {
        return threadLocalMapOfDeques.getCopyOfDequeByKey(key);
    }

    @Override
    public void clearDequeByKey(String key) {
        threadLocalMapOfDeques.clearDequeByKey(key);
    }

    /**
     * A simple implementation of ThreadLocal backed Map containing values of type
     * Deque<String>. This class is copied from SLF4J version 2.0.0
     */
    private static class ThreadLocalMapOfStacks {

        final ThreadLocal<Map<String, Deque<String>>> tlMapOfStacks = new ThreadLocal<>();

        public void pushByKey(String key, String value) {
            if (key == null)
                return;

            Map<String, Deque<String>> map = tlMapOfStacks.get();

            if (map == null) {
                map = new HashMap<>();
                tlMapOfStacks.set(map);
            }

            Deque<String> deque = map.get(key);
            if (deque == null) {
                deque = new ArrayDeque<>();
            }
            deque.push(value);
            map.put(key, deque);
        }

        public String popByKey(String key) {
            Deque<String> deque = dequeByKey(key);
            return deque == null ? null : deque.pop();
        }

        public Deque<String> getCopyOfDequeByKey(String key) {
            Deque<String> deque = dequeByKey(key);

            return deque == null
                    ? null
                    : new ArrayDeque<>(deque);
        }

        public void clearDequeByKey(String key) {
            Deque<String> deque = dequeByKey(key);
            if (deque != null) {
                deque.clear();
            }
        }

        private Deque<String> dequeByKey(String key) {
            if (key == null) {
                return null;
            }

            Map<String, Deque<String>> map = tlMapOfStacks.get();
            if (map == null) {
                return null;
            }
            return map.get(key);
        }

    }
}
