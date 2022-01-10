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
package org.apache.log4j;

import java.util.Enumeration;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentMap;

import org.apache.log4j.helpers.NullEnumeration;
import org.apache.log4j.legacy.core.CategoryUtil;
import org.apache.log4j.or.ObjectRenderer;
import org.apache.log4j.or.RendererMap;
import org.apache.log4j.spi.AppenderAttachable;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.logging.log4j.message.LocalizedMessage;
import org.apache.logging.log4j.message.MapMessage;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectMessage;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.util.StackLocatorUtil;
import org.apache.logging.log4j.util.Strings;

/**
 * Implementation of the Category class for compatibility, despite it having been deprecated a long, long time ago.
 */
public class Category implements AppenderAttachable {

    private static final String FQCN = Category.class.getName();

    /**
     * The name of this category.
     */
    protected String name;

    /**
     * Additivity is set to true by default, that is children inherit the appenders of their ancestors by default. If this
     * variable is set to <code>false</code> then the appenders found in the ancestors of this category are not used.
     * However, the children of this category will inherit its appenders, unless the children have their additivity flag set
     * to <code>false</code> too. See the user manual for more details.
     */
    protected boolean additive = true;

    /**
     * The assigned level of this category. The <code>level</code> variable need not be assigned a value in which case it is
     * inherited form the hierarchy.
     */
    volatile protected Level level;

    private RendererMap rendererMap;

    /**
     * The parent of this category. All categories have at least one ancestor which is the root category.
     */
    volatile protected Category parent;

    /**
     * Resource bundle for localized messages.
     */
    protected ResourceBundle bundle;

    private final org.apache.logging.log4j.Logger logger;

    /** Categories need to know what Hierarchy they are in. */
    protected LoggerRepository repository;

    /**
     * Constructor used by Logger to specify a LoggerContext.
     * @param context The LoggerContext.
     * @param name The name of the Logger.
     */
    protected Category(final LoggerContext context, final String name) {
        this.name = name;
        this.logger = context.getLogger(name);
        this.repository = LogManager.getLoggerRepository();
        //this.rendererMap = ((RendererSupport) repository).getRendererMap();
    }

    /**
     * Constructor exposed by Log4j 1.2.
     * @param name The name of the Logger.
     */
    protected Category(final String name) {
        this(Hierarchy.getContext(), name);
    }

    Category(final org.apache.logging.log4j.Logger logger) {
        this.logger = logger;
        //rendererMap = ((RendererSupport) LogManager.getLoggerRepository()).getRendererMap();
    }

    public static Category getInstance(final String name) {
        // Depth 2 gets the call site of this method.
        return LogManager.getLogger(name, StackLocatorUtil.getCallerClassLoader(2));
    }

    public static Category getInstance(@SuppressWarnings("rawtypes") final Class clazz) {
        // Depth 2 gets the call site of this method.
        return LogManager.getLogger(clazz.getName(), StackLocatorUtil.getCallerClassLoader(2));
    }

    public final String getName() {
        return logger.getName();
    }

    org.apache.logging.log4j.Logger getLogger() {
        return logger;
    }

    public final Category getParent() {
        if (!LogManager.isLog4jCorePresent()) {
            return null;
        }
        final org.apache.logging.log4j.Logger parent = CategoryUtil.getParent(logger);
        final LoggerContext loggerContext = CategoryUtil.getLoggerContext(logger);
        if (parent == null || loggerContext == null) {
            return null;
        }
        final ConcurrentMap<String, Logger> loggers = Hierarchy.getLoggersMap(loggerContext);
        final Logger parentLogger = loggers.get(parent.getName());
        return parentLogger == null ? new Category(parent) : parentLogger;
    }

    public static Category getRoot() {
        return getInstance(Strings.EMPTY);
    }

    /**
     * Returns all the currently defined categories in the default hierarchy as an
     * {@link java.util.Enumeration Enumeration}.
     *
     * <p>
     * The root category is <em>not</em> included in the returned
     * {@link Enumeration}.
     * </p>
     *
     * @return and Enumeration of the Categories.
     *
     * @deprecated Please use {@link LogManager#getCurrentLoggers()} instead.
     */
    @SuppressWarnings("rawtypes")
    @Deprecated
    public static Enumeration getCurrentCategories() {
        return LogManager.getCurrentLoggers(StackLocatorUtil.getCallerClassLoader(2));
    }

    /**
     * Gets the default LoggerRepository instance.
     *
     * @return the default LoggerRepository instance.
     * @deprecated Please use {@link LogManager#getLoggerRepository()} instead.
     * @since 1.0
     */
    @Deprecated
    public static LoggerRepository getDefaultHierarchy() {
        return LogManager.getLoggerRepository();
    }

    public Level getEffectiveLevel() {
        switch (logger.getLevel().getStandardLevel()) {
        case ALL:
            return Level.ALL;
        case TRACE:
            return Level.TRACE;
        case DEBUG:
            return Level.DEBUG;
        case INFO:
            return Level.INFO;
        case WARN:
            return Level.WARN;
        case ERROR:
            return Level.ERROR;
        case FATAL:
            return Level.FATAL;
        default:
            // TODO Should this be an IllegalStateException?
            return Level.OFF;
        }
    }

    /**
     * Gets the the {@link LoggerRepository} where this <code>Category</code> instance is attached.
     *
     * @deprecated Please use {@link #getLoggerRepository()} instead.
     * @since 1.1
     */
    @Deprecated
    public LoggerRepository getHierarchy() {
        return repository;
    }

    /**
     * Gets the the {@link LoggerRepository} where this <code>Category</code> is attached.
     *
     * @since 1.2
     */
    public LoggerRepository getLoggerRepository() {
        return repository;
    }

    public Priority getChainedPriority() {
        return getEffectiveLevel();
    }

    public final Level getLevel() {
        return getEffectiveLevel();
    }

    private String getLevelStr(final Priority priority) {
        return priority == null ? null : priority.levelStr;
    }

    public void setLevel(final Level level) {
        setLevel(getLevelStr(level));
    }

    public final Level getPriority() {
        return getEffectiveLevel();
    }

    public void setPriority(final Priority priority) {
        setLevel(getLevelStr(priority));
    }

    private void setLevel(final String levelStr) {
        if (LogManager.isLog4jCorePresent()) {
            CategoryUtil.setLevel(logger, org.apache.logging.log4j.Level.toLevel(levelStr));
        }
    }

    public void debug(final Object message) {
        maybeLog(FQCN, org.apache.logging.log4j.Level.DEBUG, message, null);
    }

    public void debug(final Object message, final Throwable t) {
        maybeLog(FQCN, org.apache.logging.log4j.Level.DEBUG, message, t);
    }

    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    public void error(final Object message) {
        maybeLog(FQCN, org.apache.logging.log4j.Level.ERROR, message, null);
    }

    public void error(final Object message, final Throwable t) {
        maybeLog(FQCN, org.apache.logging.log4j.Level.ERROR, message, t);
    }

    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    public void warn(final Object message) {
        maybeLog(FQCN, org.apache.logging.log4j.Level.WARN, message, null);
    }

    public void warn(final Object message, final Throwable t) {
        maybeLog(FQCN, org.apache.logging.log4j.Level.WARN, message, t);
    }

    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    public void fatal(final Object message) {
        maybeLog(FQCN, org.apache.logging.log4j.Level.FATAL, message, null);
    }

    public void fatal(final Object message, final Throwable t) {
        maybeLog(FQCN, org.apache.logging.log4j.Level.FATAL, message, t);
    }

    public boolean isFatalEnabled() {
        return logger.isFatalEnabled();
    }

    public void info(final Object message) {
        maybeLog(FQCN, org.apache.logging.log4j.Level.INFO, message, null);
    }

    public void info(final Object message, final Throwable t) {
        maybeLog(FQCN, org.apache.logging.log4j.Level.INFO, message, t);
    }

    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    public boolean isEnabledFor(final Priority level) {
        final org.apache.logging.log4j.Level lvl = org.apache.logging.log4j.Level.toLevel(level.toString());
        return isEnabledFor(lvl);
    }

    /**
     * No-op implementation.
     * @param appender The Appender to add.
     */
    @Override
    public void addAppender(final Appender appender) {
    }

    /**
     * No-op implementation.
     * @param event The logging event.
     */
    public void callAppenders(final LoggingEvent event) {
    }

    /**
     * Closes all attached appenders implementing the AppenderAttachable interface.
     *
     * @since 1.0
     */
    synchronized void closeNestedAppenders() {
        final Enumeration enumeration = this.getAllAppenders();
        if (enumeration != null) {
            while (enumeration.hasMoreElements()) {
                final Appender a = (Appender) enumeration.nextElement();
                if (a instanceof AppenderAttachable) {
                    a.close();
                }
            }
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Enumeration getAllAppenders() {
        return NullEnumeration.getInstance();
    }

    /**
     * No-op implementation.
     * @param name The name of the Appender.
     * @return null.
     */
    @Override
    public Appender getAppender(final String name) {
        return null;
    }

    /**
     Is the appender passed as parameter attached to this category?
     * @param appender The Appender to add.
     * @return true if the appender is attached.
     */
    @Override
    public boolean isAttached(final Appender appender) {
        return false;
    }

    /**
     * No-op implementation.
     */
    @Override
    public void removeAllAppenders() {
    }

    /**
     * No-op implementation.
     * @param appender The Appender to remove.
     */
    @Override
    public void removeAppender(final Appender appender) {
    }

    /**
     * No-op implementation.
     * @param name The Appender to remove.
     */
    @Override
    public void removeAppender(final String name) {
    }

    /**
     * No-op implementation.
     */
    public static void shutdown() {
    }

    public void forcedLog(final String fqcn, final Priority level, final Object message, final Throwable t) {
        final org.apache.logging.log4j.Level lvl = org.apache.logging.log4j.Level.toLevel(level.toString());
        if (logger instanceof ExtendedLogger) {
            @SuppressWarnings("unchecked")
            final
            Message msg = message instanceof Message ? (Message) message : message instanceof Map ?
                    new MapMessage((Map) message) : new ObjectMessage(message);
            ((ExtendedLogger) logger).logMessage(fqcn, lvl, null, msg, t);
        } else {
            final ObjectRenderer renderer = get(message.getClass());
            final Message msg = message instanceof Message ? (Message) message : renderer != null ?
                    new RenderedMessage(renderer, message) : new ObjectMessage(message);
            logger.log(lvl, msg, t);
        }
    }

    /**
     * Tests if the named category exists (in the default hierarchy).
     *
     * @param name The name to test.
     * @return Whether the name exists.
     *
     * @deprecated Please use {@link LogManager#exists(String)} instead.
     * @since 0.8.5
     */
    @Deprecated
    public static Logger exists(final String name) {
        return LogManager.exists(name);
    }

    public boolean getAdditivity() {
        return LogManager.isLog4jCorePresent() ? CategoryUtil.isAdditive(logger) : false;
    }

    public void setAdditivity(final boolean additivity) {
        if (LogManager.isLog4jCorePresent()) {
            CategoryUtil.setAdditivity(logger, additivity);
        }
    }

    /**
     * Only the Hiearchy class can set the hiearchy of a category. Default package access is MANDATORY here.
     */
    final void setHierarchy(final LoggerRepository repository) {
        this.repository = repository;
    }

    public void setResourceBundle(final ResourceBundle bundle) {
        this.bundle = bundle;
    }

    public ResourceBundle getResourceBundle() {
        if (bundle != null) {
            return bundle;
        }
        String name = logger.getName();
        if (LogManager.isLog4jCorePresent()) {
            final LoggerContext ctx = CategoryUtil.getLoggerContext(logger);
            if (ctx != null) {
                final ConcurrentMap<String, Logger> loggers = Hierarchy.getLoggersMap(ctx);
                while ((name = getSubName(name)) != null) {
                    final Logger subLogger = loggers.get(name);
                    if (subLogger != null) {
                        final ResourceBundle rb = subLogger.bundle;
                        if (rb != null) {
                            return rb;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static  String getSubName(final String name) {
        if (Strings.isEmpty(name)) {
            return null;
        }
        final int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : Strings.EMPTY;
    }

    /**
     * If <code>assertion</code> parameter is {@code false}, then logs
     * <code>msg</code> as an {@link #error(Object) error} statement.
     *
     * <p>
     * The <code>assert</code> method has been renamed to <code>assertLog</code>
     * because <code>assert</code> is a language reserved word in JDK 1.4.
     * </p>
     *
     * @param assertion The assertion.
     * @param msg       The message to print if <code>assertion</code> is false.
     *
     * @since 1.2
     */
    public void assertLog(final boolean assertion, final String msg) {
        if (!assertion) {
            this.error(msg);
        }
    }

    public void l7dlog(final Priority priority, final String key, final Throwable t) {
        if (isEnabledFor(priority)) {
            final Message msg = new LocalizedMessage(bundle, key, null);
            forcedLog(FQCN, priority, msg, t);
        }
    }

    public void l7dlog(final Priority priority, final String key, final Object[] params, final Throwable t) {
        if (isEnabledFor(priority)) {
            final Message msg = new LocalizedMessage(bundle, key, params);
            forcedLog(FQCN, priority, msg, t);
        }
    }

    public void log(final Priority priority, final Object message, final Throwable t) {
        if (isEnabledFor(priority)) {
            @SuppressWarnings("unchecked")
            final Message msg = message instanceof Map ? new MapMessage((Map) message) : new ObjectMessage(message);
            forcedLog(FQCN, priority, msg, t);
        }
    }

    public void log(final Priority priority, final Object message) {
        if (isEnabledFor(priority)) {
            @SuppressWarnings("unchecked")
            final Message msg = message instanceof Map ? new MapMessage((Map) message) : new ObjectMessage(message);
            forcedLog(FQCN, priority, msg, null);
        }
    }

    public void log(final String fqcn, final Priority priority, final Object message, final Throwable t) {
        if (isEnabledFor(priority)) {
            final Message msg = new ObjectMessage(message);
            forcedLog(fqcn, priority, msg, t);
        }
    }

    void maybeLog(
            final String fqcn,
            final org.apache.logging.log4j.Level level,
            final Object message,
            final Throwable throwable) {
        if (logger.isEnabled(level)) {
            final Message msg;
            if (message instanceof String) {
                msg = new SimpleMessage((String) message);
            }
            // SimpleMessage treats String and CharSequence differently, hence
            // this else-if block.
            else if (message instanceof CharSequence) {
                msg = new SimpleMessage((CharSequence) message);
            } else if (message instanceof Map) {
                @SuppressWarnings("unchecked")
                final Map<String, ?> map = (Map<String, ?>) message;
                msg = new MapMessage<>(map);
            } else {
                msg = new ObjectMessage(message);
            }
            if (logger instanceof ExtendedLogger) {
                ((ExtendedLogger) logger).logMessage(fqcn, level, null, msg, throwable);
            } else {
                logger.log(level, msg, throwable);
            }
        }
    }

    private boolean isEnabledFor(final org.apache.logging.log4j.Level level) {
        return logger.isEnabled(level);
    }

    private <T> ObjectRenderer get(final Class<T> clazz) {
        ObjectRenderer renderer = null;
        for (Class<? super T> c = clazz; c != null; c = c.getSuperclass()) {
            renderer = rendererMap.get(c);
            if (renderer != null) {
                return renderer;
            }
            renderer = searchInterfaces(c);
            if (renderer != null) {
                return renderer;
            }
        }
        return null;
    }

    ObjectRenderer searchInterfaces(final Class<?> c) {
        ObjectRenderer renderer = rendererMap.get(c);
        if (renderer != null) {
            return renderer;
        }
        final Class<?>[] ia = c.getInterfaces();
        for (final Class<?> clazz : ia) {
            renderer = searchInterfaces(clazz);
            if (renderer != null) {
                return renderer;
            }
        }
        return null;
    }

}
