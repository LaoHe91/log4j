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
package org.apache.logging.log4j.core.impl;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.plugins.di.ConfigurableInstanceFactory;
import org.apache.logging.log4j.plugins.di.DI;
import org.junit.jupiter.api.Test;

/**
 * Tests the ReusableLogEventFactory class.
 */
public class ReusableLogEventFactoryTest {

    private final ConfigurableInstanceFactory instanceFactory = DI.createInitializedFactory();

    private final ReusableLogEventFactory factory = instanceFactory.getInstance(ReusableLogEventFactory.class);

    @Test
    public void testCreateEventReturnsDifferentInstanceIfNotReleased() {
        final LogEvent event1 = callCreateEvent("a", Level.DEBUG, new SimpleMessage("abc"), null);
        final LogEvent event2 = callCreateEvent("b", Level.INFO, new SimpleMessage("xyz"), null);
        assertNotSame(event1, event2);
        factory.recycle(event1);
        factory.recycle(event2);
    }

    @Test
    public void testCreateEventReturnsSameInstance() {
        final LogEvent event1 = callCreateEvent("a", Level.DEBUG, new SimpleMessage("abc"), null);
        factory.recycle(event1);
        final LogEvent event2 = callCreateEvent("b", Level.INFO, new SimpleMessage("xyz"), null);
        assertSame(event1, event2);

        factory.recycle(event2);
        final LogEvent event3 = callCreateEvent("c", Level.INFO, new SimpleMessage("123"), null);
        assertSame(event2, event3);
        factory.recycle(event3);
    }

    @Test
    public void testCreateEventOverwritesFields() {
        final LogEvent event1 = callCreateEvent("a", Level.DEBUG, new SimpleMessage("abc"), null);
        assertEquals("a", event1.getLoggerName(), "logger");
        assertEquals(Level.DEBUG, event1.getLevel(), "level");
        assertEquals(new SimpleMessage("abc"), event1.getMessage(), "msg");

        factory.recycle(event1);
        final LogEvent event2 = callCreateEvent("b", Level.INFO, new SimpleMessage("xyz"), null);
        assertSame(event1, event2);

        assertEquals("b", event1.getLoggerName(), "logger");
        assertEquals(Level.INFO, event1.getLevel(), "level");
        assertEquals(new SimpleMessage("xyz"), event1.getMessage(), "msg");
        assertEquals("b", event2.getLoggerName(), "logger");
        assertEquals(Level.INFO, event2.getLevel(), "level");
        assertEquals(new SimpleMessage("xyz"), event2.getMessage(), "msg");
    }

    private LogEvent callCreateEvent(
            final String logger, final Level level, final Message message, final Throwable thrown) {
        return factory.createEvent(logger, null, getClass().getName(), level, message, null, thrown);
    }

    @Test
    public void testCreateEventReturnsThreadLocalInstance() throws Exception {
        final LogEvent[] event1 = new LogEvent[1];
        final LogEvent[] event2 = new LogEvent[1];
        final Thread t1 = new Thread("THREAD 1") {
            @Override
            public void run() {
                event1[0] = callCreateEvent("a", Level.DEBUG, new SimpleMessage("abc"), null);
            }
        };
        final Thread t2 = new Thread("Thread 2") {
            @Override
            public void run() {
                event2[0] = callCreateEvent("b", Level.INFO, new SimpleMessage("xyz"), null);
            }
        };
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        assertNotNull(event1[0]);
        assertNotNull(event2[0]);
        assertNotSame(event1[0], event2[0]);
        assertEquals("a", event1[0].getLoggerName(), "logger");
        assertEquals(Level.DEBUG, event1[0].getLevel(), "level");
        assertEquals(new SimpleMessage("abc"), event1[0].getMessage(), "msg");
        assertEquals("THREAD 1", event1[0].getThreadName(), "thread name");
        assertEquals(t1.getId(), event1[0].getThreadId(), "tid");

        assertEquals("b", event2[0].getLoggerName(), "logger");
        assertEquals(Level.INFO, event2[0].getLevel(), "level");
        assertEquals(new SimpleMessage("xyz"), event2[0].getMessage(), "msg");
        assertEquals("Thread 2", event2[0].getThreadName(), "thread name");
        assertEquals(t2.getId(), event2[0].getThreadId(), "tid");
        factory.recycle(event1[0]);
        factory.recycle(event2[0]);
    }

    @Test
    public void testCreateEventInitFieldsProperly() {
        final LogEvent event = callCreateEvent("logger", Level.INFO, new SimpleMessage("xyz"), null);
        try {
            assertNotNull(event.getContextData());
            assertNotNull(event.getContextStack());
        } finally {
            factory.recycle(event);
        }
    }
}
