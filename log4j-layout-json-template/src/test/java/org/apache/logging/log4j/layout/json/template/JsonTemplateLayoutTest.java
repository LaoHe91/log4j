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
package org.apache.logging.log4j.layout.json.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.SocketAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.ByteBufferDestination;
import org.apache.logging.log4j.core.lookup.MainMapLookup;
import org.apache.logging.log4j.core.net.Severity;
import org.apache.logging.log4j.core.time.MutableInstant;
import org.apache.logging.log4j.core.util.KeyValuePair;
import org.apache.logging.log4j.message.ObjectMessage;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.test.AvailablePortFinder;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;
import org.apache.logging.log4j.util.Strings;
import org.assertj.core.data.Percentage;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("DoubleBraceInitialization")
public class JsonTemplateLayoutTest {

    private static final Configuration CONFIGURATION = new DefaultConfiguration();

    private static final List<LogEvent> LOG_EVENTS = LogEventFixture.createFullLogEvents(5);

    private static final JsonNodeFactory JSON_NODE_FACTORY = JsonNodeFactory.instance;

    private static final ObjectMapper OBJECT_MAPPER = JacksonFixture.getObjectMapper();

    private static final String LOGGER_NAME = JsonTemplateLayoutTest.class.getSimpleName();

    @Test
    public void test_serialized_event() throws IOException {
        final String lookupTestKey = "lookup_test_key";
        final String lookupTestVal =
                String.format("lookup_test_value_%d", (int) (1000 * Math.random()));
        System.setProperty(lookupTestKey, lookupTestVal);
        for (final LogEvent logEvent : LOG_EVENTS) {
            checkLogEvent(logEvent, lookupTestKey, lookupTestVal);
        }
    }

    private void checkLogEvent(
            final LogEvent logEvent,
            @SuppressWarnings("SameParameterValue")
            final String lookupTestKey,
            final String lookupTestVal) throws IOException {
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplateUri("classpath:testJsonTemplateLayout.json")
                .setStackTraceEnabled(true)
                .setLocationInfoEnabled(true)
                .build();
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readValue(serializedLogEvent, JsonNode.class);
        checkConstants(rootNode);
        checkBasicFields(logEvent, rootNode);
        checkSource(logEvent, rootNode);
        checkException(layout.getCharset(), logEvent, rootNode);
        checkLookupTest(lookupTestKey, lookupTestVal, rootNode);
    }

    private static void checkConstants(final JsonNode rootNode) {
        assertThat(point(rootNode, "@version").asInt()).isEqualTo(1);
    }

    private static void checkBasicFields(final LogEvent logEvent, final JsonNode rootNode) {
        assertThat(point(rootNode, "message").asText())
                .isEqualTo(logEvent.getMessage().getFormattedMessage());
        assertThat(point(rootNode, "level").asText())
                .isEqualTo(logEvent.getLevel().name());
        assertThat(point(rootNode, "logger_fqcn").asText())
                .isEqualTo(logEvent.getLoggerFqcn());
        assertThat(point(rootNode, "logger_name").asText())
                .isEqualTo(logEvent.getLoggerName());
        assertThat(point(rootNode, "thread_id").asLong())
                .isEqualTo(logEvent.getThreadId());
        assertThat(point(rootNode, "thread_name").asText())
                .isEqualTo(logEvent.getThreadName());
        assertThat(point(rootNode, "thread_priority").asInt())
                .isEqualTo(logEvent.getThreadPriority());
        assertThat(point(rootNode, "end_of_batch").asBoolean())
                .isEqualTo(logEvent.isEndOfBatch());
    }

    private static void checkSource(final LogEvent logEvent, final JsonNode rootNode) {
        assertThat(point(rootNode, "class").asText()).isEqualTo(logEvent.getSource().getClassName());
        assertThat(point(rootNode, "file").asText()).isEqualTo(logEvent.getSource().getFileName());
        assertThat(point(rootNode, "line_number").asInt()).isEqualTo(logEvent.getSource().getLineNumber());
    }

    private static void checkException(
            final Charset charset,
            final LogEvent logEvent,
            final JsonNode rootNode) {
        final Throwable thrown = logEvent.getThrown();
        if (thrown != null) {
            assertThat(point(rootNode, "exception_class").asText()).isEqualTo(thrown.getClass().getCanonicalName());
            assertThat(point(rootNode, "exception_message").asText()).isEqualTo(thrown.getMessage());
            final String stackTrace = serializeStackTrace(charset, thrown);
            assertThat(point(rootNode, "stacktrace").asText()).isEqualTo(stackTrace);
        }
    }

    private static String serializeStackTrace(
            final Charset charset,
            final Throwable exception) {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final String charsetName = charset.name();
        try (final PrintStream printStream =
                     new PrintStream(outputStream, false, charsetName)) {
            exception.printStackTrace(printStream);
            return outputStream.toString(charsetName);
        }  catch (final UnsupportedEncodingException error) {
            throw new RuntimeException("failed converting the stack trace to string", error);
        }
    }

    private static void checkLookupTest(
            final String lookupTestKey,
            final String lookupTestVal,
            final JsonNode rootNode) {
        assertThat(point(rootNode, lookupTestKey).asText()).isEqualTo(lookupTestVal);
    }

    private static JsonNode point(final JsonNode node, final Object... fields) {
        final String pointer = createJsonPointer(fields);
        return node.at(pointer);
    }

    private static String createJsonPointer(final Object... fields) {
        final StringBuilder jsonPathBuilder = new StringBuilder();
        for (final Object field : fields) {
            jsonPathBuilder.append("/").append(field);
        }
        return jsonPathBuilder.toString();
    }

    @Test
    public void test_inline_template() throws Exception {

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("Hello, World");
        final String timestamp = "2017-09-28T17:13:29.098+02:00";
        final long timeMillis = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                .parse(timestamp)
                .getTime();
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.INFO)
                .setMessage(message)
                .setTimeMillis(timeMillis)
                .build();

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("@timestamp", "${json:timestamp:timeZone=Europe/Amsterdam}");
        final String staticFieldName = "staticFieldName";
        final String staticFieldValue = "staticFieldValue";
        eventTemplateRootNode.put(staticFieldName, staticFieldValue);
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(eventTemplate)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, "@timestamp").asText()).isEqualTo(timestamp);
        assertThat(point(rootNode, staticFieldName).asText()).isEqualTo(staticFieldValue);

    }

    @Test
    public void test_log4j_deferred_runtime_resolver_for_MapMessage() throws Exception {

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("mapValue3", "${json:message:json}");
        eventTemplateRootNode.put("mapValue1", "${map:key1}");
        eventTemplateRootNode.put("mapValue2", "${map:key2}");
        eventTemplateRootNode.put(
                "nestedLookupEmptyValue",
                "${map:noExist:-${map:noExist2:-${map:noExist3:-}}}");
        eventTemplateRootNode.put(
                "nestedLookupStaticValue",
                "${map:noExist:-${map:noExist2:-${map:noExist3:-Static Value}}}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(eventTemplate)
                .build();

        // Create the log event with a MapMessage.
        final StringMapMessage mapMessage = new StringMapMessage()
                .with("key1", "val1")
                .with("key2", "val2")
                .with("key3", Collections.singletonMap("foo", "bar"));
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.INFO)
                .setMessage(mapMessage)
                .setTimeMillis(System.currentTimeMillis())
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, "mapValue1").asText()).isEqualTo("val1");
        assertThat(point(rootNode, "mapValue2").asText()).isEqualTo("val2");
        assertThat(point(rootNode, "nestedLookupEmptyValue").asText()).isEmpty();
        assertThat(point(rootNode, "nestedLookupStaticValue").asText()).isEqualTo("Static Value");

    }

    @Test
    public void test_MapMessage_serialization() throws Exception {

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("message", "${json:message:json}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(eventTemplate)
                .build();

        // Create the log event with a MapMessage.
        final StringMapMessage mapMessage = new StringMapMessage()
                .with("key1", "val1")
                .with("key2", 0xDEADBEEF)
                .with("key3", Collections.singletonMap("key3.1", "val3.1"));
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.INFO)
                .setMessage(mapMessage)
                .setTimeMillis(System.currentTimeMillis())
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, "message", "key1").asText()).isEqualTo("val1");
        assertThat(point(rootNode, "message", "key2").asLong()).isEqualTo(0xDEADBEEF);
        assertThat(point(rootNode, "message", "key3", "key3.1").asText()).isEqualTo("val3.1");

    }

    @Test
    public void test_property_injection() throws Exception {

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("Hello, World");
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.INFO)
                .setMessage(message)
                .build();

        // Create the event template with property.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        final String propertyName = "propertyName";
        eventTemplateRootNode.put(propertyName, "${" + propertyName + "}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout with property.
        final String propertyValue = "propertyValue";
        final Configuration config = ConfigurationBuilderFactory
                .newConfigurationBuilder()
                .addProperty(propertyName, propertyValue)
                .build();
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(config)
                .setEventTemplate(eventTemplate)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, propertyName).asText()).isEqualTo(propertyValue);

    }

    @Test
    public void test_empty_root_cause() throws Exception {

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("Hello, World!");
        final RuntimeException exception = new RuntimeException("failure for test purposes");
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.ERROR)
                .setMessage(message)
                .setThrown(exception)
                .build();

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("ex_class", "${json:exception:className}");
        eventTemplateRootNode.put("ex_message", "${json:exception:message}");
        eventTemplateRootNode.put("ex_stacktrace", "${json:exception:stackTrace:text}");
        eventTemplateRootNode.put("root_ex_class", "${json:exceptionRootCause:className}");
        eventTemplateRootNode.put("root_ex_message", "${json:exceptionRootCause:message}");
        eventTemplateRootNode.put("root_ex_stacktrace", "${json:exceptionRootCause:stackTrace:text}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setStackTraceEnabled(true)
                .setEventTemplate(eventTemplate)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, "ex_class").asText())
                .isEqualTo(exception.getClass().getCanonicalName());
        assertThat(point(rootNode, "ex_message").asText())
                .isEqualTo(exception.getMessage());
        assertThat(point(rootNode, "ex_stacktrace").asText())
                .startsWith(exception.getClass().getCanonicalName() + ": " + exception.getMessage());
        assertThat(point(rootNode, "root_ex_class").asText())
                .isEqualTo(point(rootNode, "ex_class").asText());
        assertThat(point(rootNode, "root_ex_message").asText())
                .isEqualTo(point(rootNode, "ex_message").asText());
        assertThat(point(rootNode, "root_ex_stacktrace").asText())
                .isEqualTo(point(rootNode, "ex_stacktrace").asText());

    }

    @Test
    public void test_root_cause() throws Exception {

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("Hello, World!");
        final RuntimeException exceptionCause = new RuntimeException("failure cause for test purposes");
        final RuntimeException exception = new RuntimeException("failure for test purposes", exceptionCause);
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.ERROR)
                .setMessage(message)
                .setThrown(exception)
                .build();

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("ex_class", "${json:exception:className}");
        eventTemplateRootNode.put("ex_message", "${json:exception:message}");
        eventTemplateRootNode.put("ex_stacktrace", "${json:exception:stackTrace:text}");
        eventTemplateRootNode.put("root_ex_class", "${json:exceptionRootCause:className}");
        eventTemplateRootNode.put("root_ex_message", "${json:exceptionRootCause:message}");
        eventTemplateRootNode.put("root_ex_stacktrace", "${json:exceptionRootCause:stackTrace:text}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setStackTraceEnabled(true)
                .setEventTemplate(eventTemplate)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, "ex_class").asText())
                .isEqualTo(exception.getClass().getCanonicalName());
        assertThat(point(rootNode, "ex_message").asText())
                .isEqualTo(exception.getMessage());
        assertThat(point(rootNode, "ex_stacktrace").asText())
                .startsWith(exception.getClass().getCanonicalName() + ": " + exception.getMessage());
        assertThat(point(rootNode, "root_ex_class").asText())
                .isEqualTo(exceptionCause.getClass().getCanonicalName());
        assertThat(point(rootNode, "root_ex_message").asText())
                .isEqualTo(exceptionCause.getMessage());
        assertThat(point(rootNode, "root_ex_stacktrace").asText())
                .startsWith(exceptionCause.getClass().getCanonicalName() + ": " + exceptionCause.getMessage());

    }

    @Test
    public void test_marker_name() throws IOException {

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("Hello, World!");
        final String markerName = "test";
        final Marker marker = MarkerManager.getMarker(markerName);
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.ERROR)
                .setMessage(message)
                .setMarker(marker)
                .build();

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        final String messageKey = "message";
        eventTemplateRootNode.put(messageKey, "${json:message}");
        final String markerNameKey = "marker";
        eventTemplateRootNode.put(markerNameKey, "${json:marker:name}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(eventTemplate)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, messageKey).asText()).isEqualTo(message.getFormattedMessage());
        assertThat(point(rootNode, markerNameKey).asText()).isEqualTo(markerName);

    }

    @Test
    public void test_lineSeparator_suffix() {

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("Hello, World!");
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.INFO)
                .setMessage(message)
                .build();

        // Check line separators.
        test_lineSeparator_suffix(logEvent, true);
        test_lineSeparator_suffix(logEvent, false);

    }

    private void test_lineSeparator_suffix(
            final LogEvent logEvent,
            final boolean prettyPrintEnabled) {

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplateUri("classpath:LogstashJsonEventLayoutV1.json")
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final String assertionCaption = String.format("testing lineSeperator (prettyPrintEnabled=%s)", prettyPrintEnabled);
        assertThat(serializedLogEvent).as(assertionCaption).endsWith("}" + System.lineSeparator());

    }

    @Test
    public void test_main_key_access() throws IOException {

        // Set main() arguments.
        final String kwKey = "--name";
        final String kwVal = "aNameValue";
        final String positionArg = "position2Value";
        final String missingKwKey = "--missing";
        final String[] mainArgs = {kwKey, kwVal, positionArg};
        MainMapLookup.setMainArguments(mainArgs);

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("Hello, World!");
        final LogEvent logEvent = Log4jLogEvent
            .newBuilder()
            .setLoggerName(LOGGER_NAME)
            .setLevel(Level.INFO)
            .setMessage(message)
            .build();

        // Create the template.
        final ObjectNode templateRootNode = JSON_NODE_FACTORY.objectNode();
        templateRootNode.put("name", String.format("${json:main:%s}", kwKey));
        templateRootNode.put("positionArg", "${json:main:2}");
        templateRootNode.put("notFoundArg", String.format("${json:main:%s}", missingKwKey));
        final String template = templateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(template)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, "name").asText()).isEqualTo(kwVal);
        assertThat(point(rootNode, "positionArg").asText()).isEqualTo(positionArg);
        assertThat(point(rootNode, "notFoundArg")).isInstanceOf(NullNode.class);

    }

    @Test
    public void test_mdc_key_access() throws IOException {

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("Hello, World!");
        final StringMap contextData = new SortedArrayStringMap();
        final String mdcDirectlyAccessedKey = "mdcKey1";
        final String mdcDirectlyAccessedValue = "mdcValue1";
        contextData.putValue(mdcDirectlyAccessedKey, mdcDirectlyAccessedValue);
        final String mdcDirectlyAccessedNullPropertyKey = "mdcKey2";
        final String mdcDirectlyAccessedNullPropertyValue = null;
        // noinspection ConstantConditions
        contextData.putValue(mdcDirectlyAccessedNullPropertyKey, mdcDirectlyAccessedNullPropertyValue);
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.INFO)
                .setMessage(message)
                .setContextData(contextData)
                .build();

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put(
                mdcDirectlyAccessedKey,
                String.format("${json:mdc:key=%s}", mdcDirectlyAccessedKey));
        eventTemplateRootNode.put(
                mdcDirectlyAccessedNullPropertyKey,
                String.format("${json:mdc:key=%s}", mdcDirectlyAccessedNullPropertyKey));
        String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setStackTraceEnabled(true)
                .setEventTemplate(eventTemplate)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, mdcDirectlyAccessedKey).asText()).isEqualTo(mdcDirectlyAccessedValue);
        assertThat(point(rootNode, mdcDirectlyAccessedNullPropertyKey)).isInstanceOf(NullNode.class);

    }

    @Test
    public void test_mdc_pattern() throws IOException {

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("Hello, World!");
        final StringMap contextData = new SortedArrayStringMap();
        final String mdcPatternMatchedKey = "mdcKey1";
        final String mdcPatternMatchedValue = "mdcValue1";
        contextData.putValue(mdcPatternMatchedKey, mdcPatternMatchedValue);
        final String mdcPatternMismatchedKey = "mdcKey2";
        final String mdcPatternMismatchedValue = "mdcValue2";
        contextData.putValue(mdcPatternMismatchedKey, mdcPatternMismatchedValue);
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.INFO)
                .setMessage(message)
                .setContextData(contextData)
                .build();

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        final String mdcFieldName = "mdc";
        eventTemplateRootNode.put(mdcFieldName, "${json:mdc:pattern=" + mdcPatternMatchedKey + "}");
        String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setStackTraceEnabled(true)
                .setEventTemplate(eventTemplate)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, mdcFieldName, mdcPatternMatchedKey).asText()).isEqualTo(mdcPatternMatchedValue);
        assertThat(point(rootNode, mdcFieldName, mdcPatternMismatchedKey)).isInstanceOf(MissingNode.class);

    }

    @Test
    public void test_mdc_flatten() throws IOException {

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("Hello, World!");
        final StringMap contextData = new SortedArrayStringMap();
        final String mdcPatternMatchedKey = "mdcKey1";
        final String mdcPatternMatchedValue = "mdcValue1";
        contextData.putValue(mdcPatternMatchedKey, mdcPatternMatchedValue);
        final String mdcPatternMismatchedKey = "mdcKey2";
        final String mdcPatternMismatchedValue = "mdcValue2";
        contextData.putValue(mdcPatternMismatchedKey, mdcPatternMismatchedValue);
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.INFO)
                .setMessage(message)
                .setContextData(contextData)
                .build();

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        final String mdcPrefix = "_mdc.";
        eventTemplateRootNode.put(
                mdcPrefix,
                "${json:mdc:flatten=" + mdcPrefix + ",pattern=" + mdcPatternMatchedKey + "}");
        String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setStackTraceEnabled(true)
                .setEventTemplate(eventTemplate)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, mdcPrefix + mdcPatternMatchedKey).asText()).isEqualTo(mdcPatternMatchedValue);
        assertThat(point(rootNode, mdcPrefix + mdcPatternMismatchedKey)).isInstanceOf(MissingNode.class);

    }

    @Test
    public void test_MapResolver() throws IOException {

        // Create the log event.
        final StringMapMessage message = new StringMapMessage().with("key1", "val1");
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.INFO)
                .setMessage(message)
                .build();

        // Create the event template node with map values.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("mapValue1", "${json:map:key1}");
        eventTemplateRootNode.put("mapValue2", "${json:map:noExist}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(eventTemplate)
                .build();

        // Check serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, "mapValue1").asText()).isEqualTo("val1");
        assertThat(point(rootNode, "mapValue2")).isInstanceOf(NullNode.class);

    }

    @Test
    public void test_message_json() throws IOException {

        // Create the log event.
        final StringMapMessage message = new StringMapMessage();
        message.put("message", "Hello, World!");
        message.put("bottle", "Kickapoo Joy Juice");
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.INFO)
                .setMessage(message)
                .build();

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("message", "${json:message:json}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setStackTraceEnabled(true)
                .setEventTemplate(eventTemplate)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, "message", "message").asText()).isEqualTo("Hello, World!");
        assertThat(point(rootNode, "message", "bottle").asText()).isEqualTo("Kickapoo Joy Juice");

    }

    @Test
    public void test_message_json_fallback() throws IOException {

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("Hello, World!");
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.INFO)
                .setMessage(message)
                .build();

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("message", "${json:message:json}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setStackTraceEnabled(true)
                .setEventTemplate(eventTemplate)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, "message").asText()).isEqualTo("Hello, World!");

    }

    @Test
    public void test_message_object() throws IOException {

        // Create the log event.
        final int id = 0xDEADBEEF;
        final String name = "name-" + id;
        final Object attachment = new LinkedHashMap<String, Object>() {{
            put("id", id);
            put("name", name);
        }};
        final ObjectMessage message = new ObjectMessage(attachment);
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.INFO)
                .setMessage(message)
                .build();

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("message", "${json:message:json}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setStackTraceEnabled(true)
                .setEventTemplate(eventTemplate)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, "message", "id").asInt()).isEqualTo(id);
        assertThat(point(rootNode, "message", "name").asText()).isEqualTo(name);

    }

    @Test
    public void test_StackTraceElement_template() throws IOException {

        // Create the stack trace element template.
        final ObjectNode stackTraceElementTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        final String classNameFieldName = "className";
        stackTraceElementTemplateRootNode.put(
                classNameFieldName,
                "${json:stackTraceElement:className}");
        final String methodNameFieldName = "methodName";
        stackTraceElementTemplateRootNode.put(
                methodNameFieldName,
                "${json:stackTraceElement:methodName}");
        final String fileNameFieldName = "fileName";
        stackTraceElementTemplateRootNode.put(
                fileNameFieldName,
                "${json:stackTraceElement:fileName}");
        final String lineNumberFieldName = "lineNumber";
        stackTraceElementTemplateRootNode.put(
                lineNumberFieldName,
                "${json:stackTraceElement:lineNumber}");
        final String stackTraceElementTemplate = stackTraceElementTemplateRootNode.toString();

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        final String stackTraceFieldName = "stackTrace";
        eventTemplateRootNode.put(stackTraceFieldName, "${json:exception:stackTrace}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setStackTraceEnabled(true)
                .setStackTraceElementTemplate(stackTraceElementTemplate)
                .setEventTemplate(eventTemplate)
                .build();

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("Hello, World!");
        final RuntimeException exceptionCause = new RuntimeException("failure cause for test purposes");
        final RuntimeException exception = new RuntimeException("failure for test purposes", exceptionCause);
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.ERROR)
                .setMessage(message)
                .setThrown(exception)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        final JsonNode stackTraceNode = point(rootNode, stackTraceFieldName);
        assertThat(stackTraceNode.isArray()).isTrue();
        final StackTraceElement[] stackTraceElements = exception.getStackTrace();
        assertThat(stackTraceNode.size()).isEqualTo(stackTraceElements.length);
        for (int stackTraceElementIndex = 0;
             stackTraceElementIndex < stackTraceElements.length;
             stackTraceElementIndex++) {
            final StackTraceElement stackTraceElement = stackTraceElements[stackTraceElementIndex];
            final JsonNode stackTraceElementNode = stackTraceNode.get(stackTraceElementIndex);
            assertThat(stackTraceElementNode.size()).isEqualTo(4);
            assertThat(point(stackTraceElementNode, classNameFieldName).asText())
                    .isEqualTo(stackTraceElement.getClassName());
            assertThat(point(stackTraceElementNode, methodNameFieldName).asText())
                    .isEqualTo(stackTraceElement.getMethodName());
            assertThat(point(stackTraceElementNode, fileNameFieldName).asText())
                    .isEqualTo(stackTraceElement.getFileName());
            assertThat(point(stackTraceElementNode, lineNumberFieldName).asInt())
                    .isEqualTo(stackTraceElement.getLineNumber());
        }

    }

    @Test
    public void test_toSerializable_toByteArray_encode_outputs() {

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplateUri("classpath:LogstashJsonEventLayoutV1.json")
                .setStackTraceEnabled(true)
                .build();

        // Create the log event.
        final LogEvent logEvent = LogEventFixture.createFullLogEvents(1).get(0);

        // Get toSerializable() output.
        final String toSerializableOutput = layout.toSerializable(logEvent);

        // Get toByteArrayOutput().
        final byte[] toByteArrayOutputBytes = layout.toByteArray(logEvent);
        final String toByteArrayOutput = new String(
                toByteArrayOutputBytes,
                0,
                toByteArrayOutputBytes.length,
                layout.getCharset());

        // Get encode() output.
        final ByteBuffer byteBuffer = ByteBuffer.allocate(512 * 1024);
        final ByteBufferDestination byteBufferDestination = new ByteBufferDestination() {

            @Override
            public ByteBuffer getByteBuffer() {
                return byteBuffer;
            }

            @Override
            public ByteBuffer drain(final ByteBuffer ignored) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void writeBytes(final ByteBuffer data) {
                byteBuffer.put(data);
            }

            @Override
            public void writeBytes(final byte[] buffer, final int offset, final int length) {
                byteBuffer.put(buffer, offset, length);
            }

        };
        layout.encode(logEvent, byteBufferDestination);
        String encodeOutput = new String(
                byteBuffer.array(),
                0,
                byteBuffer.position(),
                layout.getCharset());

        // Compare outputs.
        assertThat(toSerializableOutput).isEqualTo(toByteArrayOutput);
        assertThat(toByteArrayOutput).isEqualTo(encodeOutput);

    }

    @Test
    public void test_maxStringLength() throws IOException {

        // Create the log event.
        final int maxStringLength = 30;
        final String excessiveMessageString = Strings.repeat("m", maxStringLength) + 'M';
        final SimpleMessage message = new SimpleMessage(excessiveMessageString);
        final Throwable thrown = new RuntimeException();
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.INFO)
                .setMessage(message)
                .setThrown(thrown)
                .build();

        // Create the event template node with map values.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        final String messageKey = "message";
        eventTemplateRootNode.put(messageKey, "${json:message}");
        final String excessiveKey = Strings.repeat("k", maxStringLength) + 'K';
        final String excessiveValue = Strings.repeat("v", maxStringLength) + 'V';
        eventTemplateRootNode.put(excessiveKey, excessiveValue);
        final String nullValueKey = "nullValueKey";
        eventTemplateRootNode.put(nullValueKey, "${json:exception:message}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(eventTemplate)
                .setMaxStringLength(maxStringLength)
                .build();

        // Check serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        final String truncatedStringSuffix =
                JsonTemplateLayoutDefaults.getTruncatedStringSuffix();
        final String truncatedMessageString =
                excessiveMessageString.substring(0, maxStringLength) +
                        truncatedStringSuffix;
        assertThat(point(rootNode, messageKey).asText()).isEqualTo(truncatedMessageString);
        final String truncatedKey =
                excessiveKey.substring(0, maxStringLength) +
                        truncatedStringSuffix;
        final String truncatedValue =
                excessiveValue.substring(0, maxStringLength) +
                        truncatedStringSuffix;
        assertThat(point(rootNode, truncatedKey).asText()).isEqualTo(truncatedValue);
        assertThat(point(rootNode, nullValueKey).isNull()).isTrue();

    }

    private static final class NonAsciiUtf8MethodNameContainingException extends RuntimeException {;

        public static final long serialVersionUID = 0;

        private static final String NON_ASCII_UTF8_TEXT = "அஆஇฬ๘";

        private static final NonAsciiUtf8MethodNameContainingException INSTANCE =
                createInstance();

        private static NonAsciiUtf8MethodNameContainingException createInstance() {
            try {
                throwException_அஆஇฬ๘();
                throw new IllegalStateException("should not have reached here");
            } catch (final NonAsciiUtf8MethodNameContainingException exception) {
                return exception;
            }
        }

        @SuppressWarnings("NonAsciiCharacters")
        private static void throwException_அஆஇฬ๘() {
            throw new NonAsciiUtf8MethodNameContainingException(
                    "exception with non-ASCII UTF-8 method name");
        }

        private NonAsciiUtf8MethodNameContainingException(final String message) {
            super(message);
        }

    }

    @Test
    public void test_exception_with_nonAscii_utf8_method_name() throws IOException {

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("Hello, World!");
        final RuntimeException exception = NonAsciiUtf8MethodNameContainingException.INSTANCE;
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(Level.ERROR)
                .setMessage(message)
                .setThrown(exception)
                .build();

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("ex_stacktrace", "${json:exception:stackTrace:text}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setStackTraceEnabled(true)
                .setEventTemplate(eventTemplate)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, "ex_stacktrace").asText())
                .contains(NonAsciiUtf8MethodNameContainingException.NON_ASCII_UTF8_TEXT);

    }

    @Test
    public void test_event_template_additional_fields() throws IOException {

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("Hello, World!");
        final RuntimeException exception = NonAsciiUtf8MethodNameContainingException.INSTANCE;
        final Level level = Level.ERROR;
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(level)
                .setMessage(message)
                .setThrown(exception)
                .build();

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("level", "${json:level}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final KeyValuePair additionalField1 = new KeyValuePair("message", "${json:message}");
        final KeyValuePair additionalField2 = new KeyValuePair("@version", "1");
        final KeyValuePair[] additionalFieldPairs = {additionalField1, additionalField2};
        final JsonTemplateLayout.EventTemplateAdditionalFields additionalFields = JsonTemplateLayout
                .EventTemplateAdditionalFields
                .newBuilder()
                .setAdditionalFields(additionalFieldPairs)
                .build();
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setStackTraceEnabled(true)
                .setEventTemplate(eventTemplate)
                .setEventTemplateAdditionalFields(additionalFields)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, "level").asText()).isEqualTo(level.name());
        assertThat(point(rootNode, additionalField1.getKey()).asText()).isEqualTo(message.getFormattedMessage());
        assertThat(point(rootNode, additionalField2.getKey()).asText()).isEqualTo(additionalField2.getValue());

    }

    @Test
    @SuppressWarnings("FloatingPointLiteralPrecision")
    public void test_timestamp_epoch_accessor() throws IOException {

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("Hello, World!");
        final Level level = Level.ERROR;
        final MutableInstant instant = new MutableInstant();
        final long instantEpochSecond = 1581082727L;
        final int instantEpochSecondNano = 982123456;
        instant.initFromEpochSecond(instantEpochSecond, instantEpochSecondNano);
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setLevel(level)
                .setMessage(message)
                .setInstant(instant)
                .build();

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        final ObjectNode epochSecsNode = eventTemplateRootNode.putObject("epochSecs");
        epochSecsNode.put("double", "${json:timestamp:epoch:secs}");
        epochSecsNode.put("long", "${json:timestamp:epoch:secs,integral}");
        epochSecsNode.put("nanos", "${json:timestamp:epoch:secs.nanos}");
        epochSecsNode.put("micros", "${json:timestamp:epoch:secs.micros}");
        epochSecsNode.put("millis", "${json:timestamp:epoch:secs.millis}");
        final ObjectNode epochMillisNode = eventTemplateRootNode.putObject("epochMillis");
        epochMillisNode.put("double", "${json:timestamp:epoch:millis}");
        epochMillisNode.put("long", "${json:timestamp:epoch:millis,integral}");
        epochMillisNode.put("nanos", "${json:timestamp:epoch:millis.nanos}");
        epochMillisNode.put("micros", "${json:timestamp:epoch:millis.micros}");
        final ObjectNode epochMicrosNode = eventTemplateRootNode.putObject("epochMicros");
        epochMicrosNode.put("double", "${json:timestamp:epoch:micros}");
        epochMicrosNode.put("long", "${json:timestamp:epoch:micros,integral}");
        epochMicrosNode.put("nanos", "${json:timestamp:epoch:micros.nanos}");
        final ObjectNode epochNanosNode = eventTemplateRootNode.putObject("epochNanos");
        epochNanosNode.put("long", "${json:timestamp:epoch:nanos}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(eventTemplate)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        final Percentage errorMargin = Percentage.withPercentage(0.001D);
        assertThat(point(rootNode, "epochSecs", "double").asDouble())
                .isCloseTo(1581082727.982123456D, errorMargin);
        assertThat(point(rootNode, "epochSecs", "long").asLong())
                .isEqualTo(1581082727L);
        assertThat(point(rootNode, "epochSecs", "nanos").asInt())
                .isEqualTo(982123456L);
        assertThat(point(rootNode, "epochSecs", "micros").asInt())
                .isEqualTo(982123L);
        assertThat(point(rootNode, "epochSecs", "millis").asInt())
                .isEqualTo(982L);
        assertThat(point(rootNode, "epochMillis", "double").asDouble())
                .isCloseTo(1581082727982.123456D, errorMargin);
        assertThat(point(rootNode, "epochMillis", "long").asLong())
                .isEqualTo(1581082727982L);
        assertThat(point(rootNode, "epochMillis", "nanos").asInt())
                .isEqualTo(123456);
        assertThat(point(rootNode, "epochMillis", "micros").asInt())
                .isEqualTo(123);
        assertThat(point(rootNode, "epochMicros", "double").asDouble())
                .isCloseTo(1581082727982123.456D, errorMargin);
        assertThat(point(rootNode, "epochMicros", "long").asLong())
                .isEqualTo(1581082727982123L);
        assertThat(point(rootNode, "epochMicros", "nanos").asInt())
                .isEqualTo(456);
        assertThat(point(rootNode, "epochNanos", "long").asLong())
                .isEqualTo(1581082727982123456L);

    }

    @Test
    public void test_level_severity() throws IOException {

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("severity", "${json:level:severity}");
        eventTemplateRootNode.put("severityCode", "${json:level:severity:code}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(eventTemplate)
                .build();

        for (final Level level : Level.values()) {

            // Create the log event.
            final SimpleMessage message = new SimpleMessage("Hello, World!");
            final LogEvent logEvent = Log4jLogEvent
                    .newBuilder()
                    .setLoggerName(LOGGER_NAME)
                    .setLevel(level)
                    .setMessage(message)
                    .build();

            // Check the serialized event.
            final String serializedLogEvent = layout.toSerializable(logEvent);
            final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
            final Severity expectedSeverity = Severity.getSeverity(level);
            final String expectedSeverityName = expectedSeverity.name();
            final int expectedSeverityCode = expectedSeverity.getCode();
            assertThat(point(rootNode, "severity").asText()).isEqualTo(expectedSeverityName);
            assertThat(point(rootNode, "severityCode").asInt()).isEqualTo(expectedSeverityCode);

        }

    }

    @Test
    public void test_exception_resolvers_against_no_exceptions() throws IOException {

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("Hello, World!");
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setMessage(message)
                .build();

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("exceptionStackTrace", "${json:exception:stackTrace}");
        eventTemplateRootNode.put("exceptionStackTraceText", "${json:exception:stackTrace:text}");
        eventTemplateRootNode.put("exceptionRootCauseStackTrace", "${json:exceptionRootCause:stackTrace}");
        eventTemplateRootNode.put("exceptionRootCauseStackTraceText", "${json:exceptionRootCause:stackTrace:text}");
        eventTemplateRootNode.put("requiredFieldTriggeringError", true);
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(eventTemplate)
                .setStackTraceEnabled(true)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, "exceptionStackTrace")).isInstanceOf(MissingNode.class);
        assertThat(point(rootNode, "exceptionStackTraceText")).isInstanceOf(MissingNode.class);
        assertThat(point(rootNode, "exceptionRootCauseStackTrace")).isInstanceOf(MissingNode.class);
        assertThat(point(rootNode, "exceptionRootCauseStackTraceText")).isInstanceOf(MissingNode.class);
        assertThat(point(rootNode, "requiredFieldTriggeringError").asBoolean()).isTrue();

    }

    @Test
    public void test_timestamp_resolver() throws IOException {

        // Create log events.
        final String logEvent1FormattedInstant = "2019-01-02T09:34:11Z";
        final LogEvent logEvent1 = createLogEventAtInstant(logEvent1FormattedInstant);
        final String logEvent2FormattedInstant = "2019-01-02T09:34:12Z";
        final LogEvent logEvent2 = createLogEventAtInstant(logEvent2FormattedInstant);
        @SuppressWarnings("UnnecessaryLocalVariable")
        final String logEvent3FormattedInstant = logEvent2FormattedInstant;
        final LogEvent logEvent3 = createLogEventAtInstant(logEvent3FormattedInstant);
        final String logEvent4FormattedInstant = "2019-01-02T09:34:13Z";
        final LogEvent logEvent4 = createLogEventAtInstant(logEvent4FormattedInstant);

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put(
                "timestamp",
                "${json:timestamp:" +
                        "pattern=yyyy-MM-dd'T'HH:mm:ss'Z'," +
                        "timeZone=UTC" +
                        "}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(eventTemplate)
                .build();

        // Check the serialized 1st event.
        final String serializedLogEvent1 = layout.toSerializable(logEvent1);
        final JsonNode rootNode1 = OBJECT_MAPPER.readTree(serializedLogEvent1);
        assertThat(point(rootNode1, "timestamp").asText()).isEqualTo(logEvent1FormattedInstant);

        // Check the serialized 2nd event.
        final String serializedLogEvent2 = layout.toSerializable(logEvent2);
        final JsonNode rootNode2 = OBJECT_MAPPER.readTree(serializedLogEvent2);
        assertThat(point(rootNode2, "timestamp").asText()).isEqualTo(logEvent2FormattedInstant);

        // Check the serialized 3rd event.
        final String serializedLogEvent3 = layout.toSerializable(logEvent3);
        final JsonNode rootNode3 = OBJECT_MAPPER.readTree(serializedLogEvent3);
        assertThat(point(rootNode3, "timestamp").asText()).isEqualTo(logEvent3FormattedInstant);

        // Check the serialized 4th event.
        final String serializedLogEvent4 = layout.toSerializable(logEvent4);
        final JsonNode rootNode4 = OBJECT_MAPPER.readTree(serializedLogEvent4);
        assertThat(point(rootNode4, "timestamp").asText()).isEqualTo(logEvent4FormattedInstant);

    }

    private static LogEvent createLogEventAtInstant(final String formattedInstant) {
        final SimpleMessage message = new SimpleMessage("LogEvent at instant " + formattedInstant);
        final long instantEpochMillis = Instant.parse(formattedInstant).toEpochMilli();
        final MutableInstant instant = new MutableInstant();
        instant.initFromEpochMilli(instantEpochMillis, 0);
        return Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setMessage(message)
                .setInstant(instant)
                .build();
    }

    @Test
    public void test_StackTraceTextResolver_with_maxStringLength() throws Exception {

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("stackTrace", "${json:exception:stackTrace:text}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final int maxStringLength = eventTemplate.length();
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(eventTemplate)
                .setMaxStringLength(maxStringLength)
                .setStackTraceEnabled(true)
                .build();

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("foo");
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setMessage(message)
                .setThrown(NonAsciiUtf8MethodNameContainingException.INSTANCE)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        assertThat(point(rootNode, "stackTrace").asText()).isNotBlank();

    }

    @Test
    public void test_null_eventDelimiter() {

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("key", "val");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(eventTemplate)
                .setEventDelimiter("\0")
                .build();

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("foo");
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setMessage(message)
                .setThrown(NonAsciiUtf8MethodNameContainingException.INSTANCE)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        assertThat(serializedLogEvent).isEqualTo(eventTemplate + '\0');

    }

    @Test
    public void test_against_SocketAppender() throws Exception {

        // Craft nasty events.
        final List<LogEvent> logEvents = createNastyLogEvents();

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("message", "${json:message}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(eventTemplate)
                .build();

        // Create the server.
        final int port = AvailablePortFinder.getNextAvailable();
        try (final JsonAcceptingTcpServer server = new JsonAcceptingTcpServer(port, 1)) {

            // Create the appender.
            final SocketAppender appender = SocketAppender
                    .newBuilder()
                    .setHost("localhost")
                    .setBufferedIo(false)
                    .setPort(port)
                    .setReconnectDelayMillis(100)
                    .setName("test")
                    .setImmediateFail(false)
                    .setIgnoreExceptions(false)
                    .setLayout(layout)
                    .build();

            // Start the appender.
            appender.start();

            // Transfer and verify the log events.
            for (int logEventIndex = 0; logEventIndex < logEvents.size(); logEventIndex++) {

                // Send the log event.
                final LogEvent logEvent = logEvents.get(logEventIndex);
                appender.append(logEvent);
                appender.getManager().flush();

                // Pull the parsed log event.
                final JsonNode node = server.receivedNodes.poll(3, TimeUnit.SECONDS);
                assertThat(node)
                        .as("logEventIndex=%d", logEventIndex)
                        .isNotNull();

                // Verify the received content.
                final String expectedMessage = logEvent.getMessage().getFormattedMessage();
                final String expectedMessageChars = explainChars(expectedMessage);
                final String actualMessage = point(node, "message").asText();
                final String actualMessageChars = explainChars(actualMessage);
                assertThat(actualMessageChars)
                        .as("logEventIndex=%d", logEventIndex)
                        .isEqualTo(expectedMessageChars);

            }

            // Verify that there were no overflows.
            assertThat(server.droppedNodeCount).isZero();

        }

    }

    private static List<LogEvent> createNastyLogEvents() {
        return createNastyMessages()
                .stream()
                .map(message -> Log4jLogEvent
                        .newBuilder()
                        .setLoggerName(LOGGER_NAME)
                        .setMessage(message)
                        .build())
                .collect(Collectors.toList());
    }

    private static List<SimpleMessage> createNastyMessages() {

        // Determine the message count and character offset.
        final int messageCount = 1024;
        final int minChar = Character.MIN_VALUE;
        final int maxChar = Character.MIN_HIGH_SURROGATE - 1;
        final int totalCharCount = maxChar - minChar + 1;
        final int charOffset = totalCharCount / messageCount;

        // Populate messages.
        List<SimpleMessage> messages = new ArrayList<>(messageCount);
        for (int messageIndex = 0; messageIndex < messageCount; messageIndex++) {
            final StringBuilder stringBuilder = new StringBuilder(messageIndex + "@");
            for (int charIndex = 0; charIndex < charOffset; charIndex++) {
                final char c = (char) (minChar + messageIndex * charOffset + charIndex);
                stringBuilder.append(c);
            }
            final String messageString = stringBuilder.toString();
            final SimpleMessage message = new SimpleMessage(messageString);
            messages.add(message);
        }
        return messages;

    }

    private static final class JsonAcceptingTcpServer extends Thread implements AutoCloseable {

        private final ServerSocket serverSocket;

        private final BlockingQueue<JsonNode> receivedNodes;

        private volatile int droppedNodeCount = 0;

        private volatile boolean closed = false;

        private JsonAcceptingTcpServer(
                final int port,
                final int capacity) throws IOException {
            this.serverSocket = new ServerSocket(port);
            this.receivedNodes = new ArrayBlockingQueue<>(capacity);
            serverSocket.setReuseAddress(true);
            serverSocket.setSoTimeout(5_000);
            setDaemon(true);
            start();
        }

        @Override
        public void run() {
            try {
                try (final Socket socket = serverSocket.accept()) {
                    final InputStream inputStream = socket.getInputStream();
                    while (!closed) {
                        final MappingIterator<JsonNode> iterator = JacksonFixture
                                .getObjectMapper()
                                .readerFor(JsonNode.class)
                                .readValues(inputStream);
                        while (iterator.hasNextValue()) {
                            final JsonNode value = iterator.nextValue();
                            synchronized (this) {
                                final boolean added = receivedNodes.offer(value);
                                if (!added) {
                                    droppedNodeCount++;
                                }
                            }
                        }
                    }
                }
            } catch (final EOFException ignored) {
                // Socket is closed.
            } catch (final Exception error) {
                if (!closed) {
                    throw new RuntimeException(error);
                }
            }
        }

        @Override
        public synchronized void close() throws InterruptedException {
            if (closed) {
                throw new IllegalStateException("shutdown has already been invoked");
            }
            closed = true;
            interrupt();
            join(3_000L);
        }

    }

    private static String explainChars(final String input) {
        return IntStream
                .range(0, input.length())
                .mapToObj(i -> {
                    final char c = input.charAt(i);
                    return String.format("'%c' (%04X)", c, (int) c);
                })
                .collect(Collectors.joining(", "));
    }

    @Test
    public void test_PatternResolver() throws IOException {

        // Create the event template.
        final ObjectNode eventTemplateRootNode = JSON_NODE_FACTORY.objectNode();
        eventTemplateRootNode.put("message", "${json:pattern:%p:%m}");
        final String eventTemplate = eventTemplateRootNode.toString();

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout
                .newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(eventTemplate)
                .build();

        // Create the log event.
        final SimpleMessage message = new SimpleMessage("foo");
        final Level level = Level.FATAL;
        final LogEvent logEvent = Log4jLogEvent
                .newBuilder()
                .setLoggerName(LOGGER_NAME)
                .setMessage(message)
                .setLevel(level)
                .build();

        // Check the serialized event.
        final String serializedLogEvent = layout.toSerializable(logEvent);
        final JsonNode rootNode = OBJECT_MAPPER.readTree(serializedLogEvent);
        final String expectedMessage = String.format(
                "%s:%s",
                level, message.getFormattedMessage());
        assertThat(point(rootNode, "message").asText()).isEqualTo(expectedMessage);

    }

}
