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
package org.apache.logging.log4j.simple;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.PrintStream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.simple.internal.SimpleProvider;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerRegistry;
import org.apache.logging.log4j.util.PropertiesUtil;

/**
 * A simple {@link LoggerContext} implementation.
 */
public class SimpleLoggerContext implements LoggerContext {

    /** Singleton instance. */
    static final SimpleLoggerContext INSTANCE = new SimpleLoggerContext();

    /** The default format to use when formatting dates */
    protected static final String DEFAULT_DATE_TIME_FORMAT = "yyyy/MM/dd HH:mm:ss:SSS zzz";

    /** All system properties used by <code>SimpleLog</code> start with this */
    protected static final String SYSTEM_PREFIX = "org.apache.logging.log4j.simplelog.";

    private final PropertiesUtil props;

    /** Include the instance name in the log message? */
    private final boolean showLogName;

    /**
     * Include the short name (last component) of the logger in the log message. Defaults to true - otherwise we'll be
     * lost in a flood of messages without knowing who sends them.
     */
    private final boolean showShortName;

    /** Include the current time in the log message */
    private final boolean showDateTime;

    /** Include the ThreadContextMap in the log message */
    private final boolean showContextMap;

    /** The date and time format to use in the log message */
    private final String dateTimeFormat;

    private final Level defaultLevel;

    private final PrintStream stream;

    private final LoggerRegistry<ExtendedLogger> loggerRegistry = new LoggerRegistry<>();

    /**
     * Constructs a new initialized instance.
     */
    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_OUT",
            justification = "Opens a file retrieved from configuration (Log4j properties)")
    public SimpleLoggerContext() {
        final SimpleProvider.Config config = SimpleProvider.Config.INSTANCE;
        props = config.props;
        showContextMap = config.showContextMap;
        showLogName = config.showLogName;
        showShortName = config.showShortName;
        showDateTime = config.showDateTime;
        defaultLevel = config.defaultLevel;
        dateTimeFormat = config.dateTimeFormat;
        stream = config.stream;
    }

    @Override
    public Object getExternalContext() {
        return null;
    }

    @Override
    public ExtendedLogger getLogger(final String name) {
        return getLogger(name, null);
    }

    @Override
    public ExtendedLogger getLogger(final String name, final MessageFactory messageFactory) {
        // Note: This is the only method where we add entries to the 'loggerRegistry' ivar.
        final ExtendedLogger extendedLogger = loggerRegistry.getLogger(name, messageFactory);
        if (extendedLogger != null) {
            return extendedLogger;
        }
        final SimpleLogger simpleLogger = new SimpleLogger(
                name,
                defaultLevel,
                showLogName,
                showShortName,
                showDateTime,
                showContextMap,
                dateTimeFormat,
                messageFactory,
                props,
                stream);
        loggerRegistry.putIfAbsent(name, messageFactory, simpleLogger);
        return loggerRegistry.getLogger(name, messageFactory);
    }

    /**
     * Gets the LoggerRegistry.
     *
     * @return the LoggerRegistry.
     * @since 2.17.2
     */
    @Override
    public LoggerRegistry<ExtendedLogger> getLoggerRegistry() {
        return loggerRegistry;
    }

    @Override
    public boolean hasLogger(final String name) {
        return false;
    }

    @Override
    public boolean hasLogger(final String name, final Class<? extends MessageFactory> messageFactoryClass) {
        return false;
    }

    @Override
    public boolean hasLogger(final String name, final MessageFactory messageFactory) {
        return false;
    }
}
