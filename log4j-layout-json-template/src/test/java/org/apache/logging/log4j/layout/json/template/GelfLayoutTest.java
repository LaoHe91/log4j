package org.apache.logging.log4j.layout.json.template;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.layout.GelfLayout;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.layout.json.template.JsonTemplateLayout.EventTemplateAdditionalField;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.apache.logging.log4j.layout.json.template.LayoutComparisonHelpers.renderUsing;

public class GelfLayoutTest {

    private static final Configuration CONFIGURATION = new DefaultConfiguration();

    private static final String HOST_NAME = "localhost";

    private static final JsonTemplateLayout JSON_TEMPLATE_LAYOUT = JsonTemplateLayout
            .newBuilder()
            .setConfiguration(CONFIGURATION)
            .setEventTemplateUri("classpath:GelfLayout.json")
            .setEventTemplateAdditionalFields(
                    JsonTemplateLayout
                            .EventTemplateAdditionalFields
                            .newBuilder()
                            .setAdditionalFields(
                                    new EventTemplateAdditionalField[]{
                                            EventTemplateAdditionalField
                                                    .newBuilder()
                                                    .setKey("host")
                                                    .setValue(HOST_NAME)
                                                    .build()
                                    })
                            .build())
            .build();

    private static final GelfLayout GELF_LAYOUT = GelfLayout
            .newBuilder()
            .setConfiguration(CONFIGURATION)
            .setHost(HOST_NAME)
            .setCompressionType(GelfLayout.CompressionType.OFF)
            .build();

    @Test
    public void test_lite_log_events() {
        final List<LogEvent> logEvents = LogEventFixture.createLiteLogEvents(1_000);
        test(logEvents);
    }

    @Test
    public void test_full_log_events() {
        final List<LogEvent> logEvents = LogEventFixture.createFullLogEvents(1_000);
        test(logEvents);
    }

    private static void test(final Collection<LogEvent> logEvents) {
        for (final LogEvent logEvent : logEvents) {
            test(logEvent);
        }
    }

    private static void test(final LogEvent logEvent) {
        final Map<String, Object> jsonTemplateLayoutMap = renderUsingJsonTemplateLayout(logEvent);
        final Map<String, Object> gelfLayoutMap = renderUsingGelfLayout(logEvent);
        verifyTimestamp(logEvent.getInstant(), jsonTemplateLayoutMap, gelfLayoutMap);
        Assertions.assertThat(jsonTemplateLayoutMap).isEqualTo(gelfLayoutMap);
    }

    private static Map<String, Object> renderUsingJsonTemplateLayout(
            final LogEvent logEvent) {
        return renderUsing(logEvent, JSON_TEMPLATE_LAYOUT);
    }

    private static Map<String, Object> renderUsingGelfLayout(
            final LogEvent logEvent) {
        return renderUsing(logEvent, GELF_LAYOUT);
    }

    /**
     * Handle timestamps individually to avoid floating-point comparison hiccups.
     */
    private static void verifyTimestamp(
            final Instant logEventInstant,
            final Map<String, Object> jsonTemplateLayoutMap,
            final Map<String, Object> gelfLayoutMap) {
        final BigDecimal jsonTemplateLayoutTimestamp =
                (BigDecimal) jsonTemplateLayoutMap.remove("timestamp");
        final BigDecimal gelfLayoutTimestamp =
                (BigDecimal) gelfLayoutMap.remove("timestamp");
        final String description = String.format(
                "instantEpochSecs=%d.%d, jsonTemplateLayoutTimestamp=%s, gelfLayoutTimestamp=%s",
                logEventInstant.getEpochSecond(),
                logEventInstant.getNanoOfSecond(),
                jsonTemplateLayoutTimestamp,
                gelfLayoutTimestamp);
        Assertions
                .assertThat(jsonTemplateLayoutTimestamp.compareTo(gelfLayoutTimestamp))
                .as(description)
                .isEqualTo(0);
    }

}
