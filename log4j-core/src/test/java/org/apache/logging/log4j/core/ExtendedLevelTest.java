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
package org.apache.logging.log4j.core;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.junit.InitialLoggerContext;
import org.apache.logging.log4j.test.ExtendedLevels;
import org.apache.logging.log4j.test.appender.ListAppender;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

/**
 *
 */
public class ExtendedLevelTest {

    private static final String CONFIG = "log4j-customLevel.xml";
    private static ListAppender list1;
    private static ListAppender list2;

    @ClassRule
    public static InitialLoggerContext context = new InitialLoggerContext(CONFIG);

    @Before
    public void before() {
        list1 = (ListAppender) context.getRequiredAppender("List1");
        list2 = (ListAppender) context.getRequiredAppender("List2");
        list1.clear();
        list2.clear();
    }

    @Test
    public void testLevelLogging() {
        org.apache.logging.log4j.Logger logger = context.getLogger("org.apache.logging.log4j.test1");
        logger.log(ExtendedLevels.DETAIL, "Detail message");
        logger.log(Level.DEBUG, "Debug message");
        List<LogEvent> events = list1.getEvents();
        assertNotNull("No events", events);
        assertEquals("Incorrect number of events. Expected 1 got " + events.size(), 1, events.size());
        LogEvent event = events.get(0);
        assertEquals("Expected level DETAIL, got" + event.getLevel(), "DETAIL", event.getLevel().name());
        logger = context.getLogger("org.apache.logging.log4j.test2");
        logger.log(ExtendedLevels.NOTE, "Note message");
        logger.log(Level.INFO, "Info message");
        events = list2.getEvents();
        assertNotNull("No events", events);
        assertEquals("Incorrect number of events. Expected 1 got " + events.size(), 1, events.size());
        event = events.get(0);
        assertEquals("Expected level NOTE, got" + event.getLevel(), "NOTE", event.getLevel().name());
    }
}
