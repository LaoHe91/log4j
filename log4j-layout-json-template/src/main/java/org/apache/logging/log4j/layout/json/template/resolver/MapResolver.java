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
package org.apache.logging.log4j.layout.json.template.resolver;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.lookup.MapLookup;
import org.apache.logging.log4j.layout.json.template.util.JsonWriter;
import org.apache.logging.log4j.message.MapMessage;

final class MapResolver implements EventResolver {

    private static final MapLookup MAP_LOOKUP = new MapLookup();

    private final String key;

    static String getName() {
        return "map";
    }

    MapResolver(final String key) {
        this.key = key;
    }

    @Override
    public boolean isResolvable(final LogEvent logEvent) {
        return logEvent.getMessage() instanceof MapMessage;
    }

    @Override
    public void resolve(
            final LogEvent logEvent,
            final JsonWriter jsonWriter) {

        // If the event message is not of type MapMessage, then do not even try
        // to perform the map lookup.
        if (!(logEvent.getMessage() instanceof MapMessage)) {
            jsonWriter.writeNull();
        }

        // Perform the map lookup against Log4j.
        else {
            final String resolvedValue = MAP_LOOKUP.lookup(logEvent, key);
            jsonWriter.writeString(resolvedValue);
        }

    }

}
