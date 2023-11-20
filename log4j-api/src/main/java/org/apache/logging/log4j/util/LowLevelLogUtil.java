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
package org.apache.logging.log4j.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.PrintWriter;
import org.apache.logging.log4j.Logger;

/**
 * PrintWriter-based logging utility for classes too low level to use {@link org.apache.logging.log4j.status.StatusLogger}.
 * Such classes cannot use StatusLogger as StatusLogger or {@link org.apache.logging.log4j.simple.SimpleLogger} depends
 * on them for initialization. Other framework classes should stick to using StatusLogger.
 *
 * @since 2.6
 */
@InternalApi
public final class LowLevelLogUtil {

    interface ErrorLogger {
        void error(final String message);

        void error(final Throwable throwable);

        void error(final String message, final Throwable throwable);
    }

    private static class StandardErrorLogger implements ErrorLogger {
        private final PrintWriter stderr = new PrintWriter(System.err, true);

        @Override
        public void error(final String message) {
            stderr.println("ERROR: " + message);
        }

        @Override
        public void error(final Throwable throwable) {
            throwable.printStackTrace(stderr);
        }

        @Override
        public void error(final String message, final Throwable throwable) {
            error(message);
            error(throwable);
        }
    }

    private static final class DelegateErrorLogger implements ErrorLogger {
        private final Logger logger;

        private DelegateErrorLogger(final Logger logger) {
            this.logger = logger;
        }

        @Override
        public void error(final String message) {
            logger.error(message);
        }

        @Override
        public void error(final Throwable throwable) {
            logger.error(throwable);
        }

        @Override
        public void error(final String message, final Throwable throwable) {
            logger.error(message, throwable);
        }
    }

    private static ErrorLogger errorLogger = new StandardErrorLogger();

    /**
     * Sets the low level logging strategy to use a delegate Logger.
     */
    public static void setLogger(final Logger logger) {
        errorLogger = new DelegateErrorLogger(logger);
    }

    private static final ThreadLocal<Boolean> guard = ThreadLocal.withInitial(() -> false);

    /**
     * Logs the given message.
     *
     * @param message the message to log
     * @since 2.9.2
     */
    public static void log(final String message) {
        if (guard.get()) {
            return;
        }
        guard.set(true);
        try {
            if (message != null) {
                errorLogger.error(message);
            }
        } finally {
            guard.set(false);
        }
    }

    @SuppressFBWarnings(
            value = "INFORMATION_EXPOSURE_THROUGH_AN_ERROR_MESSAGE",
            justification = "Log4j prints stacktraces only to logs, which should be private.")
    public static void logException(final Throwable exception) {
        if (guard.get()) {
            return;
        }
        guard.set(true);
        try {
            if (exception != null) {
                errorLogger.error(exception);
            }
        } finally {
            guard.set(false);
        }
    }

    public static void logException(final String message, final Throwable exception) {
        if (guard.get()) {
            return;
        }
        guard.set(true);
        try {
            errorLogger.error(message, exception);
        } finally {
            guard.set(false);
        }
    }

    private LowLevelLogUtil() {}
}
