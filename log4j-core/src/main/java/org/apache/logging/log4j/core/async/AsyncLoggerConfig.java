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
package org.apache.logging.log4j.core.async;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.jmx.RingBufferAdmin;
import org.apache.logging.log4j.core.util.Booleans;
import org.apache.logging.log4j.plugins.Node;
import org.apache.logging.log4j.plugins.Plugin;
import org.apache.logging.log4j.plugins.PluginAttribute;
import org.apache.logging.log4j.plugins.PluginElement;
import org.apache.logging.log4j.plugins.PluginFactory;
import org.apache.logging.log4j.plugins.validation.constraints.Required;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.apache.logging.log4j.util.Strings;

/**
 * Asynchronous Logger object that is created via configuration and can be
 * combined with synchronous loggers.
 * <p>
 * AsyncLoggerConfig is a logger designed for high throughput and low latency
 * logging. It does not perform any I/O in the calling (application) thread, but
 * instead hands off the work to another thread as soon as possible. The actual
 * logging is performed in the background thread. It uses the LMAX Disruptor
 * library for inter-thread communication. (<a
 * href="http://lmax-exchange.github.com/disruptor/"
 * >http://lmax-exchange.github.com/disruptor/</a>)
 * <p>
 * To use AsyncLoggerConfig, specify {@code <asyncLogger>} or
 * {@code <asyncRoot>} in configuration.
 * <p>
 * Note that for performance reasons, this logger does not include source
 * location by default. You need to specify {@code includeLocation="true"} in
 * the configuration or any %class, %location or %line conversion patterns in
 * your log4j.xml configuration will produce either a "?" character or no output
 * at all.
 * <p>
 * For best performance, use AsyncLoggerConfig with the RandomAccessFileAppender or
 * RollingRandomAccessFileAppender, with immediateFlush=false. These appenders have
 * built-in support for the batching mechanism used by the Disruptor library,
 * and they will flush to disk at the end of each batch. This means that even
 * with immediateFlush=false, there will never be any items left in the buffer;
 * all log events will all be written to disk in a very efficient manner.
 */
@Plugin(name = "asyncLogger", category = Node.CATEGORY, printObject = true)
public class AsyncLoggerConfig extends LoggerConfig {

    private static final ThreadLocal<Boolean> ASYNC_LOGGER_ENTERED = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final AsyncLoggerConfigDelegate delegate;

    protected AsyncLoggerConfig(final String name,
            final List<AppenderRef> appenders, final Filter filter,
            final Level level, final boolean additive,
            final Property[] properties, final Configuration config,
            final boolean includeLocation) {
        super(name, appenders, filter, level, additive, properties, config,
                includeLocation);
        delegate = config.getAsyncLoggerConfigDelegate();
        delegate.setLogEventFactory(getLogEventFactory());
    }

    protected void log(final LogEvent event, final LoggerConfigPredicate predicate) {
        // See LOG4J2-2301
        if (predicate == LoggerConfigPredicate.ALL &&
                ASYNC_LOGGER_ENTERED.get() == Boolean.FALSE &&
                // Optimization: AsyncLoggerConfig is identical to LoggerConfig
                // when no appenders are present. Avoid splitting for synchronous
                // and asynchronous execution paths until encountering an
                // AsyncLoggerConfig with appenders.
                hasAppenders()) {
            // This is the first AsnycLoggerConfig encountered by this LogEvent
            ASYNC_LOGGER_ENTERED.set(Boolean.TRUE);
            try {
                // Detect the first time we encounter an AsyncLoggerConfig. We must log
                // to all non-async loggers first.
                super.log(event, LoggerConfigPredicate.SYNCHRONOUS_ONLY);
                // Then pass the event to the background thread where
                // all async logging is executed. It is important this
                // happens at most once and after all synchronous loggers
                // have been invoked, because we lose parameter references
                // from reusable messages.
                logToAsyncDelegate(event);
            } finally {
                ASYNC_LOGGER_ENTERED.set(Boolean.FALSE);
            }
        } else {
            super.log(event, predicate);
        }
    }

    @Override
    protected void callAppenders(final LogEvent event) {
        super.callAppenders(event);
    }

    private void logToAsyncDelegate(final LogEvent event) {
        if (!isFiltered(event)) {
            // Passes on the event to a separate thread that will call
            // asyncCallAppenders(LogEvent).
            populateLazilyInitializedFields(event);
            if (!delegate.tryEnqueue(event, this)) {
                handleQueueFull(event);
            }
        }
    }

    private void handleQueueFull(final LogEvent event) {
        if (AbstractLogger.getRecursionDepth() > 1) { // LOG4J2-1518, LOG4J2-2031
            // If queue is full AND we are in a recursive call, call appender directly to prevent deadlock
            AsyncQueueFullMessageUtil.logWarningToStatusLogger();
            logToAsyncLoggerConfigsOnCurrentThread(event);
        } else {
            // otherwise, we leave it to the user preference
            final EventRoute eventRoute = delegate.getEventRoute(event.getLevel());
            eventRoute.logMessage(this, event);
        }
    }

    private void populateLazilyInitializedFields(final LogEvent event) {
        event.getSource();
        event.getThreadName();
    }

    void logInBackgroundThread(final LogEvent event) {
        delegate.enqueueEvent(event, this);
    }

    /**
     * Called by AsyncLoggerConfigHelper.RingBufferLog4jEventHandler.
     *
     * This method will log the provided event to only configs of type {@link AsyncLoggerConfig} (not
     * default {@link LoggerConfig} definitions), which will be invoked on the <b>calling thread</b>.
     */
    void logToAsyncLoggerConfigsOnCurrentThread(final LogEvent event) {
        log(event, LoggerConfigPredicate.ASYNCHRONOUS_ONLY);
    }

    private String displayName() {
        return LogManager.ROOT_LOGGER_NAME.equals(getName()) ? LoggerConfig.ROOT : getName();
    }

    @Override
    public void start() {
        LOGGER.trace("AsyncLoggerConfig[{}] starting...", displayName());
        super.start();
    }

    @Override
    public boolean stop(final long timeout, final TimeUnit timeUnit) {
        setStopping();
        super.stop(timeout, timeUnit, false);
        LOGGER.trace("AsyncLoggerConfig[{}] stopping...", displayName());
        setStopped();
        return true;
    }

    /**
     * Creates and returns a new {@code RingBufferAdmin} that instruments the
     * ringbuffer of this {@code AsyncLoggerConfig}.
     *
     * @param contextName name of the {@code LoggerContext}
     * @return a new {@code RingBufferAdmin} that instruments the ringbuffer
     */
    public RingBufferAdmin createRingBufferAdmin(final String contextName) {
        return delegate.createRingBufferAdmin(contextName, getName());
    }

    /**
     * Factory method to create a LoggerConfig.
     *
     * @param additivity True if additive, false otherwise.
     * @param level The Level to be associated with the Logger.
     * @param loggerName The name of the Logger.
     * @param includeLocation "true" if location should be passed downstream
     * @param refs An array of Appender names.
     * @param properties Properties to pass to the Logger.
     * @param config The Configuration.
     * @param filter A Filter.
     * @return A new LoggerConfig.
     * @since 3.0
     */
    @PluginFactory
    public static LoggerConfig createLogger(
            @PluginAttribute(defaultBoolean = true) final boolean additivity,
            @PluginAttribute final Level level,
            @Required(message = "Loggers cannot be configured without a name") @PluginAttribute("name") final String loggerName,
            @PluginAttribute final String includeLocation,
            @PluginElement final AppenderRef[] refs,
            @PluginElement final Property[] properties,
            @PluginConfiguration final Configuration config,
            @PluginElement final Filter filter) {
        final String name = loggerName.equals(ROOT) ? Strings.EMPTY : loggerName;
        return new AsyncLoggerConfig(name, Arrays.asList(refs), filter, level, additivity, properties, config,
                includeLocation(includeLocation));
    }

    // Note: for asynchronous loggers, includeLocation default is FALSE
    protected static boolean includeLocation(final String includeLocationConfigValue) {
        return Boolean.parseBoolean(includeLocationConfigValue);
    }

    /**
     * An asynchronous root Logger.
     */
    @Plugin(name = "asyncRoot", category = Core.CATEGORY_NAME, printObject = true)
    public static class RootLogger extends LoggerConfig {

        /**
         * @since 3.0
         */
        @PluginFactory
        public static LoggerConfig createLogger(
                @PluginAttribute final String additivity,
                @PluginAttribute final Level level,
                @PluginAttribute final String includeLocation,
                @PluginElement final AppenderRef[] refs,
                @PluginElement final Property[] properties,
                @PluginConfiguration final Configuration config,
                @PluginElement final Filter filter) {
            final List<AppenderRef> appenderRefs = Arrays.asList(refs);
            final Level actualLevel = level == null ? Level.ERROR : level;
            final boolean additive = Booleans.parseBoolean(additivity, true);
            return new AsyncLoggerConfig(LogManager.ROOT_LOGGER_NAME, appenderRefs, filter, actualLevel, additive,
                    properties, config, AsyncLoggerConfig.includeLocation(includeLocation));
        }
    }
}
