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
package org.apache.logging.log4j.core.pattern;

import java.util.List;
import java.util.Set;

final class RootThrowableRenderer extends ThrowableRenderer<ThrowableRenderer.Context> {

    private static final String WRAPPED_BY_CAPTION = "Wrapped by: ";

    RootThrowableRenderer(final List<String> ignoredPackageNames, final String lineSeparator, final int maxLineCount) {
        super(ignoredPackageNames, lineSeparator, maxLineCount);
    }

    @Override
    void renderThrowable(
            final StringBuilder buffer,
            final Throwable throwable,
            final Context context,
            final Set<Throwable> visitedThrowables,
            final String stackTraceElementSuffix) {
        renderThrowable(buffer, throwable, context, visitedThrowables, stackTraceElementSuffix, "");
    }

    private void renderThrowable(
            final StringBuilder buffer,
            final Throwable throwable,
            final Context context,
            final Set<Throwable> visitedThrowables,
            final String stackTraceElementSuffix,
            final String prefix) {
        if (visitedThrowables.contains(throwable)) {
            return;
        }
        visitedThrowables.add(throwable);
        renderCause(buffer, throwable.getCause(), context, visitedThrowables, stackTraceElementSuffix, prefix);
        renderThrowableMessage(buffer, throwable);
        buffer.append(lineSeparator);
        renderStackTraceElements(buffer, throwable, context, prefix, stackTraceElementSuffix);
        renderSuppressed(
                buffer, throwable.getSuppressed(), context, visitedThrowables, stackTraceElementSuffix, prefix + '\t');
    }

    @Override
    void renderCause(
            final StringBuilder buffer,
            final Throwable cause,
            final Context context,
            final Set<Throwable> visitedThrowables,
            final String stackTraceElementSuffix,
            final String prefix) {
        if (cause != null) {
            renderThrowable(buffer, cause, context, visitedThrowables, stackTraceElementSuffix, prefix);
            buffer.append(prefix);
            buffer.append(WRAPPED_BY_CAPTION);
        }
    }

    @Override
    void renderSuppressed(
            final StringBuilder buffer,
            final Throwable[] suppressedThrowables,
            final Context context,
            final Set<Throwable> visitedThrowables,
            final String stackTraceElementSuffix,
            final String prefix) {
        if (suppressedThrowables != null && suppressedThrowables.length != 0) {
            buffer.append(prefix);
            buffer.append(SUPPRESSED_CAPTION);
            for (final Throwable suppressedThrowable : suppressedThrowables) {
                renderThrowable(
                        buffer, suppressedThrowable, context, visitedThrowables, stackTraceElementSuffix, prefix);
            }
        }
    }
}
