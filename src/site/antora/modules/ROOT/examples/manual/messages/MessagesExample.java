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
package example;

import java.net.InetSocketAddress;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.message.SimpleMessage;

public class MessagesExample {
    private static final Logger logger = LogManager.getLogger();

    public static void main(String[] args) {
        Throwable e = new RuntimeException();
        doLogSimple(e);
        doLogParameterized("foo", "bar", e);

        InetSocketAddress socketAddress = InetSocketAddress.createUnresolved("192.0.2.17", 1234);
        LoginFailureEvent event = new LoginFailureEvent("root", socketAddress);
        failedLogin(event);
    }

    private static void doLogSimple(Throwable e) {
        // tag::simple[]
        logger.error("Houston, we have a problem.", e);
        logger.error(new SimpleMessage("Houston, we have a problem."), e);
        // end::simple[]
    }

    private static void doLogParameterized(String action, String cause, Throwable e) {
        // tag::parameterized[]
        logger.error("Unable to {}, because {} occurred", action, cause, e);
        logger.error(new ParameterizedMessage("Unable to {}, because {} occurred", action, cause), e);
        // end::parameterized[]
    }

    private static void failedLogin(LoginFailureEvent event) {
        // tag::complex[]
        logger.info(
                "Connection closed by authenticating user {} {} port {} [preauth]",
                event.userName(),
                event.remoteAddress().getHostName(),
                event.remoteAddress().getPort());
        // end::complex[]
        // tag::complex-message[]
        logger.info(event);
        // end::complex-message[]
    }

    // tag::loginFailure[]
    record LoginFailureEvent(String userName, InetSocketAddress remoteAddress) implements Message { // <1>
        @Override
        public String getFormattedMessage() { // <2>
            return "Connection closed by authenticating user " + userName() + " "
                    + remoteAddress().getHostName() + " port " + remoteAddress().getPort() + " [preauth]";
        }
        // Other methods
        // end::loginFailure[]
        @Override
        public String getFormat() {
            return "";
        }

        @Override
        public Object[] getParameters() {
            return null;
        }

        @Override
        public Throwable getThrowable() {
            return null;
        }
        // tag::loginFailure[]
    }
    // end::loginFailure[]
}
