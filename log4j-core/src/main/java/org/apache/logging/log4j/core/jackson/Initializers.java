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
package org.apache.logging.log4j.core.jackson;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import com.fasterxml.jackson.databind.Module.SetupContext;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext.ContextStack;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ExtendedStackTraceElement;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectMessage;
import org.apache.logging.log4j.util.StringMap;

/**
 * Initialization utils.
 * <p>
 * <em>Consider this class private.</em>
 * </p>
 */
class Initializers {

    private abstract static class AbstractInitializer {

        void setupModule(
                final SetupContext context, final boolean includeStacktrace, final boolean stacktraceAsString) {
            // JRE classes: we cannot edit those with Jackson annotations
            context.setMixInAnnotations(StackTraceElement.class, StackTraceElementMixIn.class);
            // Log4j API classes: we do not want to edit those with Jackson annotations because the API module should
            // not depend on Jackson.
            context.setMixInAnnotations(Marker.class, MarkerMixIn.class);
            context.setMixInAnnotations(Level.class, LevelMixIn.class);
            context.setMixInAnnotations(Instant.class, InstantMixIn.class);
            context.setMixInAnnotations(LogEvent.class, LogEventJsonMixIn.class);
            // Log4j Core classes: we do not want to bring in Jackson at runtime if we do not have to.
            context.setMixInAnnotations(ExtendedStackTraceElement.class, ExtendedStackTraceElementMixIn.class);
            context.setMixInAnnotations(
                    ThrowableProxy.class,
                    includeStacktrace
                            ? (stacktraceAsString
                                    ? ThrowableProxyWithStacktraceAsStringMixIn.class
                                    : ThrowableProxyMixIn.class)
                            : ThrowableProxyWithoutStacktraceMixIn.class);
        }
    }
    /**
     * Used to set up {@link SetupContext} from different {@link SimpleModule}s.
     * <p>
     *     Serializes the context map as list of objects.
     * </p>
     */
    static class SetupContextAsEntryListInitializer extends AbstractInitializer {

        @Override
        void setupModule(
                final SetupContext context, final boolean includeStacktrace, final boolean stacktraceAsString) {
            super.setupModule(context, includeStacktrace, stacktraceAsString);
            // Prevents reflective JPMS access
            // https://github.com/apache/logging-log4j2/issues/2814
            context.addSerializers(new SimpleSerializers(singletonList(new ContextDataAsEntryListSerializer())));
            context.addDeserializers(
                    new SimpleDeserializers(singletonMap(StringMap.class, new ContextDataAsEntryListDeserializer())));
        }
    }

    /**
     * Used to set up {@link SetupContext} from different {@link SimpleModule}s.
     * <p>
     *     Serializes the context map as object.
     * </p>
     */
    static class SetupContextInitializer extends AbstractInitializer {

        @Override
        void setupModule(
                final SetupContext context, final boolean includeStacktrace, final boolean stacktraceAsString) {
            super.setupModule(context, includeStacktrace, stacktraceAsString);
            // Prevents reflective JPMS access
            // https://github.com/apache/logging-log4j2/issues/2814
            context.addSerializers(new SimpleSerializers(singletonList(new ContextDataSerializer())));
            context.addDeserializers(
                    new SimpleDeserializers(singletonMap(StringMap.class, new ContextDataDeserializer())));
        }
    }

    /**
     * Used to set up {@link SimpleModule} from different {@link SimpleModule} subclasses.
     */
    static class SimpleModuleInitializer {
        void initialize(final SimpleModule simpleModule, final boolean objectMessageAsJsonObject) {
            // Workaround because mix-ins do not work for classes that already have a built-in deserializer.
            // See Jackson issue 429.
            simpleModule.addDeserializer(StackTraceElement.class, new Log4jStackTraceElementDeserializer());
            simpleModule.addDeserializer(ContextStack.class, new MutableThreadContextStackDeserializer());
            if (objectMessageAsJsonObject) {
                simpleModule.addSerializer(ObjectMessage.class, new ObjectMessageSerializer());
            }
            simpleModule.addSerializer(Message.class, new MessageSerializer());
        }
    }
}
