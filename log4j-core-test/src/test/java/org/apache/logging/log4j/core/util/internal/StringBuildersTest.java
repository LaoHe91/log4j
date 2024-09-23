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
package org.apache.logging.log4j.core.util.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StringBuildersTest {

    static Stream<Arguments> truncateAfterDelimiter_should_succeed_inputs() {
        return Stream.of(
                // maxOccurrenceCount < lines count
                Arguments.of("abc#def#ghi#jkl#", "#", 2),
                // maxOccurrenceCount == lines count
                Arguments.of("abc#def#ghi#jkl#", "#", 4),
                // maxOccurrenceCount > lines count
                Arguments.of("abc#def#ghi#jkl#", "#", 10),
                // maxOccurrenceCount ==  Integer.MAX_VALUE
                Arguments.of("abc#def#ghi#jkl#", "#", Integer.MAX_VALUE),
                // maxOccurrenceCount ==  0
                Arguments.of("abc#def#ghi#jkl#", "#", 0),
                // empty buffer
                Arguments.of("", "#", 2),
                // empty delimiter
                Arguments.of("abc#def#ghi#jkl#", "", 2),
                // delimiter #
                Arguments.of("#", "#", 1),
                Arguments.of("##", "#", 1),
                Arguments.of("a#", "#", 1),
                Arguments.of("#a", "#", 1),
                // delimiter ##
                Arguments.of("##", "##", 1),
                Arguments.of("###", "##", 1),
                Arguments.of("####", "##", 1),
                Arguments.of("a#", "##", 1),
                Arguments.of("a##", "##", 1),
                Arguments.of("a###", "##", 1),
                Arguments.of("a####", "##", 1),
                Arguments.of("#a", "##", 1),
                Arguments.of("##a", "##", 1),
                Arguments.of("###a", "##", 1),
                Arguments.of("####a", "##", 1));
    }

    @ParameterizedTest
    @MethodSource("truncateAfterDelimiter_should_succeed_inputs")
    void truncateAfterDelimiter_should_succeed(
            final String input, final String delimiter, final int maxOccurrenceCount) {
        final StringBuilder buffer = new StringBuilder(input);
        StringBuilders.truncateAfterDelimiter(buffer, delimiter, maxOccurrenceCount);
        final String expected;
        if (delimiter.isEmpty()) {
            expected = input;
        } else if (buffer.length() == 0) {
            expected = "";
        } else {
            expected = Arrays.stream(input.split(delimiter))
                    .limit(maxOccurrenceCount)
                    .collect(Collectors.joining(delimiter, "", delimiter));
        }
        assertThat(buffer.toString()).isEqualTo(expected);
    }

    static Stream<Arguments> truncateAfterDelimiter_should_fail_inputs() {
        return Stream.of(
                // negative maxOccurrenceCount
                Arguments.of("abc#def#ghi#jkl#", "#", -1, IllegalArgumentException.class),
                // null buffer
                Arguments.of(null, "#", 10, NullPointerException.class),
                // null delimiter
                Arguments.of("abc#def#ghi#jkl#", null, 10, NullPointerException.class));
    }

    @ParameterizedTest
    @MethodSource("truncateAfterDelimiter_should_fail_inputs")
    void truncateAfterDelimiter_should_fail(
            final String input, final String delimiter, final int maxOccurrenceCount, final Class<Throwable> expected) {
        final StringBuilder buffer = input == null ? null : new StringBuilder(input);
        assertThatThrownBy(() -> StringBuilders.truncateAfterDelimiter(buffer, delimiter, maxOccurrenceCount))
                .isInstanceOf(expected);
    }
}
