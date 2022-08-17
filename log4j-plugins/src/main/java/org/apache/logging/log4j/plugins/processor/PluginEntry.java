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

package org.apache.logging.log4j.plugins.processor;

import java.util.function.Supplier;

/**
 * Descriptor for {@link org.apache.logging.log4j.plugins.Plugin} metadata.
 */
public class PluginEntry {
    private final String key;
    private final String className;
    private final String name;
    private final String elementType;
    private final boolean printable;
    private final boolean deferChildren;
    private final String namespace;
    private final Class<?>[] interfaces;

    public PluginEntry(
            String key, String className, String name, String elementType, boolean printable, boolean deferChildren, String namespace) {
        this.key = key;
        this.className = className;
        this.name = name;
        this.elementType = elementType;
        this.printable = printable;
        this.deferChildren = deferChildren;
        this.namespace = namespace;
        this.interfaces = null;
    }

    public PluginEntry(
            final String key, final String className, final String name, final String elementType, final boolean printable,
            final boolean deferChildren, final String namespace, final Class<?>... interfaces) {
        this.key = key;
        this.className = className;
        this.name = name;
        this.elementType = elementType;
        this.printable = printable;
        this.deferChildren = deferChildren;
        this.namespace = namespace;
        this.interfaces = interfaces;
    }

    private PluginEntry(final Builder builder) {
        key = builder.getKey();
        className = builder.getClassName();
        name = builder.getName();
        elementType = builder.getElementType();
        printable = builder.isPrintable();
        deferChildren = builder.isDeferChildren();
        namespace = builder.getNamespace();
        final Class<?>[] classes = builder.getInterfaces();
        interfaces = classes != null ? classes.clone() : null;
    }

    public String getKey() {
        return key;
    }

    public String getClassName() {
        return className;
    }

    public String getName() {
        return name;
    }

    public String getElementType() {
        return elementType;
    }

    public boolean isPrintable() {
        return printable;
    }

    public boolean isDeferChildren() {
        return deferChildren;
    }

    public String getNamespace() {
        return namespace;
    }

    public Class<?>[] getInterfaces() {
        return interfaces;
    }

    @Override
    public String toString() {
        return "PluginEntry [key=" + key + ", className=" + className + ", name=" + name + ", printable=" + printable
                + ", defer=" + deferChildren + ", namespace=" + namespace + "]";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder implements Supplier<PluginEntry> {
        private String key;
        private String className;
        private String name;
        private String elementType;
        private boolean printable;
        private boolean deferChildren;
        private String namespace;
        private Class<?>[] interfaces;

        public String getKey() {
            return key;
        }

        public Builder setKey(final String key) {
            this.key = key;
            return this;
        }

        public String getClassName() {
            return className;
        }

        public Builder setClassName(final String className) {
            this.className = className;
            return this;
        }

        public String getName() {
            return name;
        }

        public Builder setName(final String name) {
            this.name = name;
            return this;
        }

        public String getElementType() {
            return elementType;
        }

        public Builder setElementType(final String elementType) {
            this.elementType = elementType;
            return this;
        }

        public boolean isPrintable() {
            return printable;
        }

        public Builder setPrintable(final boolean printable) {
            this.printable = printable;
            return this;
        }

        public boolean isDeferChildren() {
            return deferChildren;
        }

        public Builder setDeferChildren(final boolean deferChildren) {
            this.deferChildren = deferChildren;
            return this;
        }

        public String getNamespace() {
            return namespace;
        }

        public Builder setNamespace(final String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Class<?>[] getInterfaces() {
            return interfaces;
        }

        public Builder setInterfaces(final Class<?>... interfaces) {
            this.interfaces = interfaces;
            return this;
        }

        @Override
        public PluginEntry get() {
            return new PluginEntry(this);
        }
    }
}
