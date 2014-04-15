/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.logging.log4j.streams;

import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.junit.InitialLoggerContext;
import org.apache.logging.log4j.test.appender.ListAppender;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;

@Ignore
public class StreamTesting {
    protected final static String NEWLINE = System.getProperty("line.separator");
    protected final static Level LEVEL = Level.ERROR;
    protected final static String FIRST = "first";
    protected final static String LAST = "last";

    @ClassRule
    public static InitialLoggerContext ctx = new InitialLoggerContext("log4j2-streams-unit-test.xml");

    protected static Logger getLogger() {
        return ctx.getLogger("UnitTestLogger");
    }

    @Before
    public void clearAppender() {
        ((ListAppender) ctx.getAppender("UnitTest")).clear();
    }

    protected void assertMessages(final String... messages) {
        List<String> actualMsgs = ((ListAppender) ctx.getAppender("UnitTest")).getMessages();
        assertEquals("Unexpected number of results.", messages.length, actualMsgs.size());
        for (int i = 0; i < messages.length; i++) {
            final String start = LEVEL.name() + ' ' + messages[i];
            assertThat(actualMsgs.get(i), startsWith(start));
        }
    }
}
