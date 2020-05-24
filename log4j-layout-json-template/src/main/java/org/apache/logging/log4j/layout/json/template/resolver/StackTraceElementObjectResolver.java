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

import org.apache.logging.log4j.layout.json.template.util.JsonWriter;

final class StackTraceElementObjectResolver implements TemplateResolver<StackTraceElement> {

    private static final TemplateResolver<StackTraceElement> CLASS_NAME_RESOLVER =
            (final StackTraceElement stackTraceElement, final JsonWriter jsonWriter) ->
                    jsonWriter.writeString(stackTraceElement.getClassName());

    private static final TemplateResolver<StackTraceElement> METHOD_NAME_RESOLVER =
            (final StackTraceElement stackTraceElement, final JsonWriter jsonWriter) ->
                    jsonWriter.writeString(stackTraceElement.getMethodName());

    private static final TemplateResolver<StackTraceElement> FILE_NAME_RESOLVER =
            (final StackTraceElement stackTraceElement, final JsonWriter jsonWriter) ->
                    jsonWriter.writeString(stackTraceElement.getFileName());

    private static final TemplateResolver<StackTraceElement> LINE_NUMBER_RESOLVER =
            (final StackTraceElement stackTraceElement, final JsonWriter jsonWriter) ->
                    jsonWriter.writeNumber(stackTraceElement.getLineNumber());

    private final TemplateResolver<StackTraceElement> internalResolver;

    StackTraceElementObjectResolver(final String key) {
        this.internalResolver = createInternalResolver(key);
    }

    private TemplateResolver<StackTraceElement> createInternalResolver(final String key) {
        switch (key) {
            case "className": return CLASS_NAME_RESOLVER;
            case "methodName": return METHOD_NAME_RESOLVER;
            case "fileName": return FILE_NAME_RESOLVER;
            case "lineNumber": return LINE_NUMBER_RESOLVER;
        }
        throw new IllegalArgumentException("unknown key: " + key);
    }

    static String getName() {
        return "stackTraceElement";
    }

    @Override
    public void resolve(
            final StackTraceElement stackTraceElement,
            final JsonWriter jsonWriter) {
        internalResolver.resolve(stackTraceElement, jsonWriter);
    }

}
