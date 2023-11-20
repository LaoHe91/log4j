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
package org.apache.logging.log4j.test.junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.logging.log4j.test.TestProperties;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.apache.logging.log4j.util.PropertyEnvironment;
import org.junit.jupiter.api.Test;

@UsingTestProperties
public class TestPropertySourceTest {

    private static TestProperties staticProperties;
    private TestProperties instanceProperties;

    @Test
    public void testInjectedFields() {
        assertThat(staticProperties).isNotNull();
        assertThat(instanceProperties).isNotNull();

        // Test that per-class properties are overridden by per-test properties
        final PropertyEnvironment env = PropertiesUtil.getProperties();
        staticProperties.setProperty("static.Property", "static");
        staticProperties.setProperty("instance.Property", "static");
        instanceProperties.setProperty("instance.Property", "instance");
        assertThat(env.getStringProperty("static.Property")).isEqualTo("static");
        assertThat(env.getStringProperty("instance.Property")).isEqualTo("instance");
    }

    @Test
    public void testInjectedParameter(final TestProperties paramProperties) {
        assertThat(paramProperties).isEqualTo(instanceProperties);
    }

    @Test
    @SetTestProperty(key = "test.SetTestProperty", value = "true")
    public void testSetTestProperty() {
        final PropertyEnvironment env = PropertiesUtil.getProperties();
        assertThat(env.getBooleanProperty("test.SetTestProperty")).isTrue();
    }
}
