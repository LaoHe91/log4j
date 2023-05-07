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
package org.apache.logging.log4j.core.util;

import java.util.Map;
import java.util.function.Supplier;

import javax.crypto.SecretKey;

import org.apache.logging.log4j.plugins.Namespace;
import org.apache.logging.log4j.plugins.di.Key;

/**
 * Factory class to provide a {@link SecretKey} instance.
 */
public interface SecretKeyProvider {

    String CATEGORY = "KeyProvider";

    Key<Map<String, Supplier<SecretKeyProvider>>> PLUGIN_MAP_KEY = new @Namespace(CATEGORY) Key<>() {};

    /**
     * Returns this SecretKey.
     *
     * @return the SecretKey.
     */
    SecretKey getSecretKey();
}
