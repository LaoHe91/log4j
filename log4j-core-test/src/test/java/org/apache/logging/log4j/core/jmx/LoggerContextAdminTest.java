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
package org.apache.logging.log4j.core.jmx;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.test.junit.SetTestProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class LoggerContextAdminTest {

    @Test
    @SetTestProperty(key = Server.PROPERTY_JMX_READ_ONLY, value="true")
    public void testReadOnly() {
        final LoggerContext ctx = createLoggerContext();
        final LoggerContextAdminMBean mbean = new LoggerContextAdmin(ctx, null);
        assertThrows(UnsupportedOperationException.class, () -> mbean.setConfigLocationUri(null));
        assertThrows(UnsupportedOperationException.class, () -> mbean.setConfigText(null, null));
    }

    @Test
    @SetTestProperty(key = Server.PROPERTY_JMX_READ_ONLY, value="false")
    public void testWriteAccess() throws URISyntaxException {
        final LoggerContext ctx = createLoggerContext();
        final LoggerContextAdmin mbean = new LoggerContextAdmin(ctx, null);
        verify(ctx).getName();
        verify(ctx).addPropertyChangeListener(mbean);
        URI location = getClass().getResource("/log4j-console.xml").toURI();
        assertDoesNotThrow(() -> mbean.setConfigLocationUri(location.toString()));
        assertDoesNotThrow(() -> mbean.setConfigText("<Configuration><Loggers><Root/></Loggers></Configuration>", "UTF-8"));
        verify(ctx, times(2)).start(any(Configuration.class));
        verifyNoMoreInteractions(ctx);
    }

    private LoggerContext createLoggerContext() {
        final LoggerContext ctx = mock(LoggerContext.class);
        when(ctx.getName()).thenReturn(LoggerContextAdminTest.class.getName());
        return ctx;
    }
}
