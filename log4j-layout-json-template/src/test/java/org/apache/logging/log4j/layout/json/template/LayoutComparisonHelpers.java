package org.apache.logging.log4j.layout.json.template;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;

import java.util.Map;

enum LayoutComparisonHelpers {;

    @SuppressWarnings("unchecked")
    static Map<String, Object> renderUsing(
            final LogEvent logEvent,
            final Layout<String> layout)
            throws Exception {
        final String json = layout.toSerializable(logEvent);
        return JacksonFixture.getObjectMapper().readValue(json, Map.class);
    }

}
