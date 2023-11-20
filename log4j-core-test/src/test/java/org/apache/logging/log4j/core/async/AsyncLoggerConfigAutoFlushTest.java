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
package org.apache.logging.log4j.core.async;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.impl.Log4jPropertyKey;
import org.apache.logging.log4j.core.test.CoreLoggerContexts;
import org.apache.logging.log4j.test.junit.CleanUpFiles;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetSystemProperty;

@Tag("async")
@SetSystemProperty(key = Log4jPropertyKey.Constant.CONFIG_LOCATION, value = "AsyncLoggerConfigAutoFlushTest.xml")
public class AsyncLoggerConfigAutoFlushTest {

    @Test
    @CleanUpFiles("target/AsyncLoggerConfigAutoFlushTest.log")
    public void testFlushAtEndOfBatch() throws Exception {
        final File file = new File("target", "AsyncLoggerConfigAutoFlushTest.log");
        assertTrue(!file.exists() || file.delete(), "Deleted old file before test");

        final Logger log = LogManager.getLogger("com.foo.Bar");
        final String msg = "Message flushed with immediate flush=false";
        log.info(msg);
        CoreLoggerContexts.stopLoggerContext(file); // stop async thread
        final String contents = Files.readString(file.toPath());
        assertTrue(contents.contains(msg), "line1 correct");
    }
}
