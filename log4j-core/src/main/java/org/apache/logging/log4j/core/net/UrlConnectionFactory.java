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
package org.apache.logging.log4j.core.net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.JarURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;

import org.apache.logging.log4j.core.impl.Log4jProperties;
import org.apache.logging.log4j.core.net.ssl.LaxHostnameVerifier;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.core.net.ssl.SslConfigurationFactory;
import org.apache.logging.log4j.core.util.AuthorizationProvider;
import org.apache.logging.log4j.plugins.ContextScoped;
import org.apache.logging.log4j.plugins.Inject;
import org.apache.logging.log4j.util.Cast;
import org.apache.logging.log4j.util.InternalApi;
import org.apache.logging.log4j.util.PropertyResolver;

/**
 * Constructs an HTTPURLConnection. This class should be considered to be internal
 */
@InternalApi
@ContextScoped
public class UrlConnectionFactory {

    private static final int DEFAULT_TIMEOUT = (int) TimeUnit.MINUTES.toMillis(1);
    private static final int connectTimeoutMillis = DEFAULT_TIMEOUT;
    private static final int readTimeoutMillis = DEFAULT_TIMEOUT;
    private static final String JSON = "application/json";
    private static final String XML = "application/xml";
    private static final String PROPERTIES = "text/x-java-properties";
    private static final String TEXT = "text/plain";
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final String JAR = "jar";
    private static final List<String> DEFAULT_ALLOWED_PROTOCOLS = List.of("https", "file", "jar");
    private static final String NO_PROTOCOLS = "_none";
    public static final String ALLOWED_PROTOCOLS = Log4jProperties.CONFIG_ALLOWED_PROTOCOLS;

    private final PropertyResolver propertyResolver;
    private final AuthorizationProvider authorizationProvider;
    private final SslConfigurationFactory sslConfigurationFactory;

    @Inject
    public UrlConnectionFactory(final PropertyResolver propertyResolver,
                                final AuthorizationProvider authorizationProvider,
                                final SslConfigurationFactory sslConfigurationFactory) {
        this.propertyResolver = propertyResolver;
        this.authorizationProvider = authorizationProvider;
        this.sslConfigurationFactory = sslConfigurationFactory;
    }

    public <T extends URLConnection> T openConnection(final URL url, final long lastModifiedMillis) throws IOException {
        List<String> allowed = propertyResolver.getList(Log4jProperties.CONFIG_ALLOWED_PROTOCOLS);
        if (allowed.isEmpty()) {
            allowed = DEFAULT_ALLOWED_PROTOCOLS;
        }
        if (allowed.size() == 1 && NO_PROTOCOLS.equals(allowed.get(0))) {
            throw new ProtocolException("No external protocols have been enabled");
        }
        final String protocol = url.getProtocol();
        if (protocol == null) {
            throw new ProtocolException("No protocol was specified on " + url.toString());
        }
        if (!allowed.contains(protocol)) {
            throw new ProtocolException("Protocol " + protocol + " has not been enabled as an allowed protocol");
        }
        URLConnection urlConnection;
        if (url.getProtocol().equals(HTTP) || url.getProtocol().equals(HTTPS)) {
            final HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            if (authorizationProvider != null) {
                authorizationProvider.addAuthorization(httpURLConnection);
            }
            httpURLConnection.setAllowUserInteraction(false);
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setDoInput(true);
            httpURLConnection.setRequestMethod("GET");
            if (connectTimeoutMillis > 0) {
                httpURLConnection.setConnectTimeout(connectTimeoutMillis);
            }
            if (readTimeoutMillis > 0) {
                httpURLConnection.setReadTimeout(readTimeoutMillis);
            }
            final String[] fileParts = url.getFile().split("\\.");
            final String type = fileParts[fileParts.length - 1].trim();
            final String contentType = isXml(type) ? XML : isJson(type) ? JSON : isProperties(type) ? PROPERTIES : TEXT;
            httpURLConnection.setRequestProperty("Content-Type", contentType);
            if (lastModifiedMillis > 0) {
                httpURLConnection.setIfModifiedSince(lastModifiedMillis);
            }
            final SslConfiguration sslConfiguration = sslConfigurationFactory.get();
            if (url.getProtocol().equals(HTTPS) && sslConfiguration != null) {
                ((HttpsURLConnection) httpURLConnection).setSSLSocketFactory(sslConfiguration.getSslSocketFactory());
                if (!sslConfiguration.isVerifyHostName()) {
                    ((HttpsURLConnection) httpURLConnection).setHostnameVerifier(LaxHostnameVerifier.INSTANCE);
                }
            }
            urlConnection = httpURLConnection;
        } else if (url.getProtocol().equals(JAR)) {
            urlConnection = url.openConnection();
            urlConnection.setUseCaches(false);
        } else {
            urlConnection = url.openConnection();
        }
        return Cast.cast(urlConnection);
    }

    public URLConnection openConnection(final URL url) throws IOException {
        final URLConnection urlConnection;
        if (url.getProtocol().equals(HTTPS) || url.getProtocol().equals(HTTP)) {
            urlConnection = openConnection(url, 0);
        } else {
            urlConnection = url.openConnection();
            if (urlConnection instanceof JarURLConnection) {
                // A "jar:" URL file remains open after the stream is closed, so do not cache it.
                urlConnection.setUseCaches(false);
            }
        }
        return urlConnection;
    }

    private static boolean isXml(final String type) {
        return type.equalsIgnoreCase("xml");
    }

    private static boolean isJson(final String type) {
        return type.equalsIgnoreCase("json") || type.equalsIgnoreCase("jsn");
    }

    private static boolean isProperties(final String type) {
        return type.equalsIgnoreCase("properties");
    }
}
