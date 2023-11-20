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
package org.apache.logging.log4j.core.async;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.logging.log4j.core.impl.Log4jPropertyKey;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junitpioneer.jupiter.SetSystemProperty;

/**
 * Test class loading deadlock condition from the LOG4J2-1457
 */
@Tag("async")
@SetSystemProperty(
        key = Log4jPropertyKey.Constant.CONTEXT_SELECTOR_CLASS_NAME,
        value = "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector")
@SetSystemProperty(key = Log4jPropertyKey.Constant.ASYNC_LOGGER_RING_BUFFER_SIZE, value = "128")
@SetSystemProperty(key = Log4jPropertyKey.Constant.CONFIG_LOCATION, value = "AsyncLoggerConsoleTest.xml")
public class AsyncLoggerClassLoadDeadlockTest {

    static final int RING_BUFFER_SIZE = 128;

    @Test
    @Timeout(value = 30)
    public void testClassLoaderDeadlock() throws Exception {
        // touch the class so static init will be called
        final AsyncLoggerClassLoadDeadlock temp = new AsyncLoggerClassLoadDeadlock();
        assertNotNull(temp);
    }
}
