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
package org.apache.logging.log4j.core.config.plugins;

import java.util.Map;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.core.test.junit.LoggerContextSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.*;

@LoggerContextSource("legacy-plugins.xml")
public class LegacyPluginTest {

    @Test
    public void testLegacy(final Configuration configuration) throws Exception {
        assertThat(configuration, instanceOf(XmlConfiguration.class));
        for (Map.Entry<String, Appender> entry : configuration.getAppenders().entrySet()) {
            if (entry.getKey().equalsIgnoreCase("console")) {
                final Layout layout = entry.getValue().getLayout();
                assertNotNull("No layout for Console Appender");
                final String name = layout.getClass().getSimpleName();
                assertEquals("LogstashLayout", name, "Incorrect Layout class. Expected LogstashLayout, Actual " + name);
            } else if (entry.getKey().equalsIgnoreCase("customConsole")) {
                final Layout layout = entry.getValue().getLayout();
                assertNotNull("No layout for CustomConsole Appender");
                final String name = layout.getClass().getSimpleName();
                assertEquals("CustomConsoleLayout",
                        name, "Incorrect Layout class. Expected CustomConsoleLayout, Actual " + name);
            }
        }
    }
}
