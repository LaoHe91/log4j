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
package org.apache.logging.log4j.core.layout;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.StringLayout;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.impl.LogEventFactory;
import org.apache.logging.log4j.core.util.GarbageFreeConfiguration;
import org.apache.logging.log4j.core.util.StringEncoder;
import org.apache.logging.log4j.plugins.Inject;
import org.apache.logging.log4j.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.plugins.PluginElement;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.apache.logging.log4j.util.Recycler;
import org.apache.logging.log4j.util.RecyclerFactories;
import org.apache.logging.log4j.util.RecyclerFactory;
import org.apache.logging.log4j.util.StringBuilders;
import org.apache.logging.log4j.util.Strings;

/**
 * Abstract base class for Layouts that result in a String.
 * <p>
 * Since 2.4.1, this class has custom logic to convert ISO-8859-1 or US-ASCII Strings to byte[] arrays to improve
 * performance: all characters are simply cast to bytes.
 * </p>
 */
public abstract class AbstractStringLayout extends AbstractLayout<String> implements StringLayout {

    public abstract static class Builder<B extends Builder<B>> extends AbstractLayout.Builder<B> {

        @PluginBuilderAttribute(value = "charset")
        private Charset charset;

        @PluginElement("footerSerializer")
        private Serializer footerSerializer;

        @PluginElement("headerSerializer")
        private Serializer headerSerializer;

        private RecyclerFactory recyclerFactory;

        public Charset getCharset() {
            return charset;
        }

        public Serializer getFooterSerializer() {
            return footerSerializer;
        }

        public Serializer getHeaderSerializer() {
            return headerSerializer;
        }

        public RecyclerFactory getRecyclerFactory() {
            if (recyclerFactory == null) {
                final Configuration configuration = getConfiguration();
                recyclerFactory = configuration != null
                        ? configuration.getInstance(RecyclerFactory.class)
                        : RecyclerFactories.ofSpec(null);
            }
            return recyclerFactory;
        }

        public B setCharset(final Charset charset) {
            this.charset = charset;
            return asBuilder();
        }

        public B setFooterSerializer(final Serializer footerSerializer) {
            this.footerSerializer = footerSerializer;
            return asBuilder();
        }

        public B setHeaderSerializer(final Serializer headerSerializer) {
            this.headerSerializer = headerSerializer;
            return asBuilder();
        }

        @Inject
        public B setRecyclerFactory(final RecyclerFactory recyclerFactory) {
            this.recyclerFactory = recyclerFactory;
            return asBuilder();
        }
    }

    public interface Serializer extends Serializer2 {
        String toSerializable(final LogEvent event);

        default boolean requiresLocation() {
            return false;
        }

        @Override
        default StringBuilder toSerializable(final LogEvent event, final StringBuilder builder) {
            builder.append(toSerializable(event));
            return builder;
        }
    }

    /**
     * Variation of {@link Serializer} that avoids allocating temporary objects.
     * As of 2.13 this interface was merged into the Serializer interface.
     * @since 2.6
     */
    public interface Serializer2  {
        StringBuilder toSerializable(final LogEvent event, final StringBuilder builder);
    }

    /**
     * Default length for new StringBuilder instances: {@value} .
     */
    // TODO(ms): this could be configurable
    protected static final int DEFAULT_STRING_BUILDER_SIZE = 1024;

    protected static final int MAX_STRING_BUILDER_SIZE = Math.max(DEFAULT_STRING_BUILDER_SIZE,
            GarbageFreeConfiguration.getDefaultConfiguration().getLayoutStringBuilderMaxSize());

    protected static void trimToMaxSize(final StringBuilder stringBuilder) {
        StringBuilders.trimToMaxSize(stringBuilder, MAX_STRING_BUILDER_SIZE);
    }

    private Encoder<StringBuilder> textEncoder;

    /**
     * The charset for the formatted message.
     */
    private final Charset charset;

    private final Serializer footerSerializer;

    private final Serializer headerSerializer;

    private final Recycler<StringBuilder> recycler;

    protected AbstractStringLayout(final Charset charset) {
        this(charset, null, null);
    }

    /**
     * Builds a new layout.
     * @param aCharset the charset used to encode the header bytes, footer bytes and anything else that needs to be
     *      converted from strings to bytes.
     * @param header the header bytes
     * @param footer the footer bytes
     */
    protected AbstractStringLayout(final Charset aCharset, final byte[] header, final byte[] footer) {
        this(aCharset, header, footer, null);
    }

    protected AbstractStringLayout(final Charset aCharset, final byte[] header, final byte[] footer,
                                   final RecyclerFactory recyclerFactory) {
        super(null, header, footer);
        this.headerSerializer = null;
        this.footerSerializer = null;
        this.charset = aCharset != null ? aCharset : StandardCharsets.UTF_8;
        final GarbageFreeConfiguration gfConfig = GarbageFreeConfiguration.getDefaultConfiguration();
        textEncoder = gfConfig.isDirectEncodersEnabled() ? new StringBuilderEncoder(charset) : null;
        final RecyclerFactory factory = recyclerFactory != null ? recyclerFactory : RecyclerFactories.ofSpec(null);
        recycler = factory.create(
                () -> new StringBuilder(DEFAULT_STRING_BUILDER_SIZE),
                buf -> {
                    StringBuilders.trimToMaxSize(buf, gfConfig.getLayoutStringBuilderMaxSize());
                    buf.setLength(0);
                }
        );
    }

    /**
     * Builds a new layout.
     * @param config the configuration
     * @param aCharset the charset used to encode the header bytes, footer bytes and anything else that needs to be
     *      converted from strings to bytes.
     * @param headerSerializer the header bytes serializer
     * @param footerSerializer the footer bytes serializer
     */
    protected AbstractStringLayout(final Configuration config, final Charset aCharset,
            final Serializer headerSerializer, final Serializer footerSerializer) {
        this(config, aCharset, headerSerializer, footerSerializer, null);
    }

    protected AbstractStringLayout(final Configuration config, final Charset aCharset,
                                   final Serializer headerSerializer, final Serializer footerSerializer,
                                   final RecyclerFactory recyclerFactory) {
        super(config, null, null);
        this.headerSerializer = headerSerializer;
        this.footerSerializer = footerSerializer;
        this.charset = aCharset == null ? StandardCharsets.UTF_8 : aCharset;
        final GarbageFreeConfiguration gfConfig = config != null
                ? config.getInstance(GarbageFreeConfiguration.class)
                : GarbageFreeConfiguration.getDefaultConfiguration();
        textEncoder = gfConfig.isDirectEncodersEnabled()
                ? new StringBuilderEncoder(charset)
                : null;
        final RecyclerFactory factory;
        if (recyclerFactory != null) {
            factory = recyclerFactory;
        } else if (config != null) {
            factory = config.getInstance(RecyclerFactory.class);
        } else {
            factory = RecyclerFactories.ofSpec(null);
        }
        recycler = factory.create(
                () -> new StringBuilder(DEFAULT_STRING_BUILDER_SIZE),
                buf -> {
                    StringBuilders.trimToMaxSize(buf, gfConfig.getLayoutStringBuilderMaxSize());
                    buf.setLength(0);
                }
        );
    }

    /**
     * Returns a {@code StringBuilder} that this Layout implementation can use to write the formatted log event to.
     *
     * @return a {@code StringBuilder}
     */
    protected StringBuilder getStringBuilder() {
        if (AbstractLogger.getRecursionDepth() > 1) { // LOG4J2-2368
            // Recursive logging may clobber the cached StringBuilder.
            return new StringBuilder(DEFAULT_STRING_BUILDER_SIZE);
        }
        return recycler.acquire();
    }

    protected void recycleStringBuilder(final StringBuilder builder) {
        recycler.release(builder);
    }

    protected byte[] getBytes(final String s) {
        return s.getBytes(charset);
    }

    @Override
    public Charset getCharset() {
        return charset;
    }

    /**
     * @return The default content type for Strings.
     */
    @Override
    public String getContentType() {
        return "text/plain";
    }

    /**
     * Returns the footer, if one is available.
     *
     * @return A byte array containing the footer.
     */
    @Override
    public byte[] getFooter() {
        return serializeToBytes(footerSerializer, super.getFooter());
    }

    public Serializer getFooterSerializer() {
        return footerSerializer;
    }

    /**
     * Returns the header, if one is available.
     *
     * @return A byte array containing the header.
     */
    @Override
    public byte[] getHeader() {
        return serializeToBytes(headerSerializer, super.getHeader());
    }

    public Serializer getHeaderSerializer() {
        return headerSerializer;
    }

    /**
     * Returns a {@code Encoder<StringBuilder>} that this Layout implementation can use for encoding log events.
     *
     * @return a {@code Encoder<StringBuilder>}
     */
    protected Encoder<StringBuilder> getStringBuilderEncoder() {
        if (textEncoder == null) {
            textEncoder = new StringBuilderEncoder(getCharset());
        }
        return textEncoder;
    }

    protected byte[] serializeToBytes(final Serializer serializer, final byte[] defaultValue) {
        final String serializable = serializeToString(serializer);
        if (serializable == null) {
            return defaultValue;
        }
        return StringEncoder.toBytes(serializable, getCharset());
    }

    protected String serializeToString(final Serializer serializer) {
        if (serializer == null) {
            return null;
        }
        final LoggerConfig rootLogger = getConfiguration().getRootLogger();
        // Using "" for the FQCN, does it matter?

        final LogEvent logEvent = getConfiguration()
                .getInstance(LogEventFactory.class)
                .createEvent(rootLogger.getName(), null, Strings.EMPTY, rootLogger.getLevel(), null, null, null);
        return serializer.toSerializable(logEvent);
    }

    /**
     * Formats the Log Event as a byte array.
     *
     * @param event The Log Event.
     * @return The formatted event as a byte array.
     */
    @Override
    public byte[] toByteArray(final LogEvent event) {
        return getBytes(toSerializable(event));
    }

}
