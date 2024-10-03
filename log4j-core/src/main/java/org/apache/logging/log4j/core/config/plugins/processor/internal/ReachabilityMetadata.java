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
package org.apache.logging.log4j.core.config.plugins.processor.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;

/**
 * Provides support for the
 * <a href="https://www.graalvm.org/latest/reference-manual/native-image/metadata/#specifying-metadata-with-json">{@code reachability-metadata.json}</a>
 * file format.
 */
@NullMarked
public final class ReachabilityMetadata {

    /**
     * Key used to specify the name of a field or method
     */
    public static final String FIELD_OR_METHOD_NAME = "name";
    /**
     * Key used to list the method parameter types.
     */
    public static final String PARAMETER_TYPES = "parameterTypes";
    /**
     * Key used to specify the name of a type.
     * <p>
     *     Since GraalVM for JDK 23 it will be called "type".
     * </p>
     */
    public static final String TYPE_NAME = "name";
    /**
     * Key used to specify the list of fields available for reflection.
     */
    public static final String FIELDS = "fields";
    /**
     * Key used to specify the list of methods available for reflection.
     */
    public static final String METHODS = "methods";

    private static class MinimalJsonWriter {

        private static final int ESCAPED_CHARS_LENGHT = 128;

        /**
         * BitSet of 7-bit ASCII characters that would require quoting in JSON.
         */
        private static final BitSet ESCAPED_CHARS;

        static {
            BitSet requiresLookup = new BitSet(ESCAPED_CHARS_LENGHT);
            // Control chars need generic escape sequence
            for (int i = 0; i < 32; ++i) {
                requiresLookup.set(i);
            }
            // Others (and some within that range too) have explicit shorter sequences
            requiresLookup.set('"');
            requiresLookup.set('\\');
            requiresLookup.set(0x08);
            requiresLookup.set(0x09);
            requiresLookup.set(0x0C);
            requiresLookup.set(0x0A);
            requiresLookup.set(0x0D);
            ESCAPED_CHARS = requiresLookup;
        }

        private final Appendable output;

        public MinimalJsonWriter(Appendable output) {
            this.output = output;
        }

        /**
         * Writes a JSON String ignoring characters that need quoting.
         */
        public void writeString(CharSequence input) throws IOException {
            output.append('"');
            int limit = input.length();
            for (int i = 0; i < limit; ++i) {
                char c = input.charAt(i);
                if (c >= ESCAPED_CHARS_LENGHT || !ESCAPED_CHARS.get(c)) {
                    output.append(c);
                }
            }
            output.append('"');
        }

        public void writeObjectStart() throws IOException {
            output.append('{');
        }

        public void writeObjectEnd() throws IOException {
            output.append('}');
        }

        public void writeObjectKey(CharSequence key) throws IOException {
            writeString(key);
            output.append(':');
        }

        public void writeArrayStart() throws IOException {
            output.append('[');
        }

        public void writeSeparator() throws IOException {
            output.append(',');
        }

        public void writeArrayEnd() throws IOException {
            output.append(']');
        }
    }

    /**
     * Specifies a field that needs to be accessed through reflection.
     */
    public static class Field implements Comparable<Field> {

        private final String name;

        public Field(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        void toJson(MinimalJsonWriter jsonWriter) throws IOException {
            jsonWriter.writeObjectStart();
            jsonWriter.writeObjectKey(FIELD_OR_METHOD_NAME);
            jsonWriter.writeString(name);
            jsonWriter.writeObjectEnd();
        }

        @Override
        public int compareTo(Field other) {
            return name.compareTo(other.name);
        }
    }

    /**
     * Specifies a method that needs to be accessed through reflection.
     */
    public static class Method implements Comparable<Method> {

        private final String name;
        private final List<String> parameterTypes = new ArrayList<>();

        public Method(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void addParameterType(final String parameterType) {
            parameterTypes.add(parameterType);
        }

        void toJson(MinimalJsonWriter jsonWriter) throws IOException {
            jsonWriter.writeObjectStart();
            jsonWriter.writeObjectKey(FIELD_OR_METHOD_NAME);
            jsonWriter.writeString(name);
            jsonWriter.writeSeparator();
            jsonWriter.writeObjectKey(PARAMETER_TYPES);
            jsonWriter.writeArrayStart();
            boolean first = true;
            for (String parameterType : parameterTypes) {
                if (!first) {
                    jsonWriter.writeSeparator();
                }
                first = false;
                jsonWriter.writeString(parameterType);
            }
            jsonWriter.writeArrayEnd();
            jsonWriter.writeObjectEnd();
        }

        @Override
        public int compareTo(Method other) {
            int result = name.compareTo(other.name);
            if (result == 0) {
                result = parameterTypes.size() - other.parameterTypes.size();
            }
            if (result == 0) {
                result = IntStream.range(0, parameterTypes.size())
                        .map(idx -> parameterTypes.get(idx).compareTo(other.parameterTypes.get(idx)))
                        .filter(r -> r != 0)
                        .findFirst()
                        .orElse(0);
            }
            return result;
        }
    }

    /**
     * Specifies a Java type that needs to be accessed through reflection.
     */
    public static class Type {

        private final String type;
        private final Collection<Method> methods = new TreeSet<>();
        private final Collection<Field> fields = new TreeSet<>();

        public Type(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void addMethod(Method method) {
            methods.add(method);
        }

        public void addField(Field field) {
            fields.add(field);
        }

        void toJson(MinimalJsonWriter jsonWriter) throws IOException {
            jsonWriter.writeObjectStart();
            jsonWriter.writeObjectKey(TYPE_NAME);
            jsonWriter.writeString(type);
            jsonWriter.writeSeparator();

            boolean first = true;
            jsonWriter.writeObjectKey(METHODS);
            jsonWriter.writeArrayStart();
            for (Method method : methods) {
                if (!first) {
                    jsonWriter.writeSeparator();
                }
                first = false;
                method.toJson(jsonWriter);
            }
            jsonWriter.writeArrayEnd();
            jsonWriter.writeSeparator();

            first = true;
            jsonWriter.writeObjectKey(FIELDS);
            jsonWriter.writeArrayStart();
            for (Field field : fields) {
                if (!first) {
                    jsonWriter.writeSeparator();
                }
                first = false;
                field.toJson(jsonWriter);
            }
            jsonWriter.writeArrayEnd();
            jsonWriter.writeObjectEnd();
        }
    }

    /**
     * Collection of reflection metadata.
     */
    public static class Reflection {

        private final Collection<Type> types = new TreeSet<>(Comparator.comparing(Type::getType));

        public void addType(Type type) {
            types.add(type);
        }

        void toJson(MinimalJsonWriter jsonWriter) throws IOException {
            boolean first = true;
            jsonWriter.writeArrayStart();
            for (Type type : types) {
                if (!first) {
                    jsonWriter.writeSeparator();
                }
                first = false;
                type.toJson(jsonWriter);
            }
            jsonWriter.writeArrayEnd();
        }
    }

    /**
     * Writes the contents of a {@code reflect-config.json} file.
     *
     * @param reflection The reflection metadata.
     * @param output The object to use as output.
     */
    public static void writeReflectConfig(Reflection reflection, Appendable output) throws IOException {
        MinimalJsonWriter jsonWriter = new MinimalJsonWriter(output);
        reflection.toJson(jsonWriter);
    }

    private ReachabilityMetadata() {}
}
