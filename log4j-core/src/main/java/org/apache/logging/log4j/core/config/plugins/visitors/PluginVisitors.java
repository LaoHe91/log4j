/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.logging.log4j.core.config.plugins.visitors;

import java.lang.annotation.Annotation;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.PluginVisitorStrategy;
import org.apache.logging.log4j.status.StatusLogger;

/**
 * Utility class to locate an appropriate PluginVisitor implementation for an annotation.
 */
public final class PluginVisitors {

    private static final Logger LOGGER = StatusLogger.getLogger();

    private PluginVisitors() {
    }

    /**
     * Creates a PluginVisitor instance for the given annotation class using metadata provided by the annotation's
     * {@link PluginVisitorStrategy} annotation. This instance must be further populated with
     * data to be useful. Such data is passed through both the setters and the visit method.
     *
     * @param annotation the Plugin annotation class to find a PluginVisitor for.
     * @param <A>        the Plugin annotation type.
     * @return a PluginVisitor instance if one could be created, or {@code null} otherwise.
     */
    @SuppressWarnings("unchecked") // we're keeping track of types, thanks
    public static <A extends Annotation> PluginVisitor<A> findVisitor(final Class<A> annotation) {
        final PluginVisitorStrategy strategy = annotation.getAnnotation(PluginVisitorStrategy.class);
        if (strategy == null) {
            LOGGER.debug("No PluginVisitorStrategy found on annotation [{}]. Ignoring.", annotation);
            return null;
        }
        final String visitorClassName = strategy.value();
        try {
            // if a PluginVisitor is in a different JAR than log4j-core, it can be safely assumed that the
            // corresponding annotation is in the same JAR as the PluginVisitor implementation. thus, we use that
            // ClassLoader instead of any default one
            final Class<? extends PluginVisitor<A>> visitorClass =
                (Class<? extends PluginVisitor<A>>) annotation.getClassLoader().loadClass(visitorClassName);
            return visitorClass.newInstance();
        } catch (final Exception e) {
            LOGGER.error("Error loading PluginVisitor [{}] for annotation [{}].", visitorClassName, annotation, e);
            return null;
        }
    }
}
