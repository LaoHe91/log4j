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
package org.apache.logging.log4j.jackson.json.template.layout.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.status.StatusLogger;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class UrisTest {

    private static final Logger LOGGER = StatusLogger.getLogger();

    @Test
    public void testClassPathResource() {
        final String content = Uris.readUri(
                "classpath:JsonLayout.json",
                StandardCharsets.US_ASCII);
        Assert.assertTrue(
                "was expecting content to start with '{', found: " + content,
                content.startsWith("{"));
    }

    @Test
    public void testFilePathResource() throws IOException {
        final String nonAsciiUtf8Text = "அஆஇฬ๘";
        final File file = Files.createTempFile("log4j-UriUtilTest-", ".txt").toFile();
        try {
            try (final OutputStream outputStream = new FileOutputStream(file)) {
                outputStream.write(nonAsciiUtf8Text.getBytes(StandardCharsets.UTF_8));
            }
            final String uri = String.format("file:%s", file.getAbsoluteFile());
            final String content = Uris.readUri(uri, StandardCharsets.UTF_8);
            Assert.assertEquals(nonAsciiUtf8Text, content);
        } finally {
            final boolean deleted = file.delete();
            if (!deleted) {
                LOGGER.warn("could not delete temporary file: " + file);
            }
        }
    }

}
