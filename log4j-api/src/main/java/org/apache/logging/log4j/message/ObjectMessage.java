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
package org.apache.logging.log4j.message;

import org.apache.logging.log4j.util.StringBuilderFormattable;
import org.apache.logging.log4j.util.StringBuilders;

/**
 * Handles messages that contain an Object.
 */
public class ObjectMessage implements Message, StringBuilderFormattable {

    private final Object obj;
    private String objectString;

    /**
     * Creates the ObjectMessage.
     *
     * @param obj The Object to format.
     */
    public ObjectMessage(final Object obj) {
        this.obj = obj == null ? "null" : obj;
    }

    /**
     * Returns the formatted object message.
     *
     * @return the formatted object message.
     */
    @Override
    public String getFormattedMessage() {
        // LOG4J2-763: cache formatted string in case obj changes later
        if (objectString == null) {
            objectString = String.valueOf(obj);
        }
        return objectString;
    }

    @Override
    public void formatTo(final StringBuilder buffer) {
        if (objectString != null) { //
            buffer.append(objectString);
        } else {
            StringBuilders.appendValue(buffer, obj);
        }
    }

    /**
     * Returns the object formatted using its toString method.
     *
     * @return the String representation of the object.
     */
    @Override
    public String getFormat() {
        return getFormattedMessage();
    }

    /**
     * Returns the object parameter.
     *
     * @return The object.
     * @since 2.7
     */
    public Object getParameter() {
        return obj;
    }

    /**
     * Returns the object as if it were a parameter.
     *
     * @return The object.
     */
    @Override
    public Object[] getParameters() {
        return new Object[] {obj};
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ObjectMessage)) {
            return false;
        }

        final ObjectMessage that = (ObjectMessage) o;
        return equalObjectsOrStrings(obj, that.obj);
    }

    private boolean equalObjectsOrStrings(final Object left, final Object right) {
        return left.equals(right) || String.valueOf(left).equals(String.valueOf(right));
    }

    @Override
    public int hashCode() {
        return obj.hashCode();
    }

    @Override
    public String toString() {
        return getFormattedMessage();
    }

    /**
     * Gets the message if it is a throwable.
     *
     * @return the message if it is a throwable.
     */
    @Override
    public Throwable getThrowable() {
        return obj instanceof Throwable ? (Throwable) obj : null;
    }
}
