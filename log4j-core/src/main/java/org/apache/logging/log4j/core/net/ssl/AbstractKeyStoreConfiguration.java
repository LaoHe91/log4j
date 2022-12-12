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
package org.apache.logging.log4j.core.net.ssl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.NoSuchFileException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;

import org.apache.logging.log4j.core.util.FileUtils;
import org.apache.logging.log4j.core.util.Loader;
import org.apache.logging.log4j.core.util.NetUtils;
import org.apache.logging.log4j.plugins.PluginAttribute;

/**
 * Configuration of the KeyStore
 */
public class AbstractKeyStoreConfiguration extends StoreConfiguration<KeyStore> {
    static final char[] DEFAULT_PASSWORD = "changeit".toCharArray();

    private final KeyStore keyStore;
    private final String keyStoreType;

    public AbstractKeyStoreConfiguration(final String location, final PasswordProvider passwordProvider, final String keyStoreType)
            throws StoreConfigurationException {
        super(location, passwordProvider);
        this.keyStoreType = keyStoreType == null ? SslConfigurationDefaults.KEYSTORE_TYPE : keyStoreType;
        this.keyStore = this.load();
    }

    @Override
    protected KeyStore load() throws StoreConfigurationException {
        final String loadLocation = this.getLocation();
        LOGGER.debug("Loading keystore from location {}", loadLocation);
        try {
            if (loadLocation == null) {
                throw new IOException("The location is null");
            }
            try (final InputStream fin = openInputStream(loadLocation)) {
                final KeyStore ks = KeyStore.getInstance(this.keyStoreType);
                final char[] password = this.getPassword();
                try {
                    ks.load(fin, password != null ? password : DEFAULT_PASSWORD);
                } finally {
                    if (password != null) {
                        Arrays.fill(password, '\0');
                    }
                }
                LOGGER.debug("KeyStore successfully loaded from location {}", loadLocation);
                return ks;
            }
        } catch (final CertificateException e) {
            LOGGER.error("No Provider supports a KeyStoreSpi implementation for the specified type {} for location {}", this.keyStoreType, loadLocation, e);
            throw new StoreConfigurationException(loadLocation, e);
        } catch (final NoSuchAlgorithmException e) {
            LOGGER.error("The algorithm used to check the integrity of the keystore cannot be found for location {}", loadLocation, e);
            throw new StoreConfigurationException(loadLocation, e);
        } catch (final KeyStoreException e) {
            LOGGER.error("KeyStoreException for location {}", loadLocation, e);
            throw new StoreConfigurationException(loadLocation, e);
        } catch (final FileNotFoundException e) {
            LOGGER.error("The keystore file {} is not found", loadLocation, e);
            throw new StoreConfigurationException(loadLocation, e);
        } catch (final IOException e) {
            LOGGER.error("Something is wrong with the format of the keystore or the given password for location {}", loadLocation, e);
            throw new StoreConfigurationException(loadLocation, e);
        }
    }

    private InputStream openInputStream(final String filePathOrUri) {
        // to avoid a circular dependency between SslConfigurationFactory and UrlConnectionFactory, we replicate
        // some of essential logic without supporting more specific ssl configuration as this is part of the ssl
        // configuration in the first place
        final URI uri = NetUtils.toURI(filePathOrUri);
        try {
            final File file = FileUtils.fileFromUri(uri);
            if (file != null) {
                return new FileInputStream(file);
            }
        } catch (final FileNotFoundException e) {
            LOGGER.warn("Cannot locate file {}", filePathOrUri);
        }
        final String scheme = uri.getScheme();
        if (scheme == null || "classpath".equals(scheme) || "classloader".equals(scheme)) {
            final String resource = scheme == null ? uri.getPath() : uri.getSchemeSpecificPart();
            return Loader.getResourceAsStream(resource, null);
        }
        try {
            final URL url = uri.toURL();
            final URLConnection urlConnection = url.openConnection();
            // A "jar:" URL file remains open after the stream is closed, so do not cache it.
            urlConnection.setUseCaches(false);
            return urlConnection.getInputStream();
        } catch (final IOException e) {
            throw new UncheckedIOException("Cannot load file path or URI: " + filePathOrUri, e);
        }
    }

    public KeyStore getKeyStore() {
        return this.keyStore;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((keyStore == null) ? 0 : keyStore.hashCode());
        result = prime * result + ((keyStoreType == null) ? 0 : keyStoreType.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AbstractKeyStoreConfiguration other = (AbstractKeyStoreConfiguration) obj;
        if (keyStore == null) {
            if (other.keyStore != null) {
                return false;
            }
        } else if (!keyStore.equals(other.keyStore)) {
            return false;
        }
        if (keyStoreType == null) {
            if (other.keyStoreType != null) {
                return false;
            }
        } else if (!keyStoreType.equals(other.keyStoreType)) {
            return false;
        }
        return true;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public static abstract class Builder<B extends Builder<B, C>, C extends AbstractKeyStoreConfiguration>
            extends StoreConfiguration.Builder<B, C, KeyStore> {
        private String type;

        public String getKeyStoreType() {
            return type;
        }

        public B setKeyStoreType(@PluginAttribute final String type) {
            this.type = type;
            return asBuilder();
        }

        protected PasswordProvider buildPasswordProvider() throws StoreConfigurationException {
            final char[] password = getPassword();
            final String passwordEnvironmentVariable = getPasswordEnvironmentVariable();
            final String passwordFile = getPasswordFile();
            if (password != null && passwordEnvironmentVariable != null && passwordFile != null) {
                throw new StoreConfigurationException("You MUST set only one of 'password', 'passwordEnvironmentVariable' or 'passwordFile'.");
            }
            final PasswordProvider passwordProvider;
            if (passwordFile != null) {
                try {
                    return new FilePasswordProvider(passwordFile);
                } catch (final NoSuchFileException e) {
                    throw new StoreConfigurationException("Unable to configure TrustStore with password file", e);
                }
            } else if (passwordEnvironmentVariable != null) {
                passwordProvider = new EnvironmentPasswordProvider(passwordEnvironmentVariable);
            } else {
                passwordProvider = new MemoryPasswordProvider(password);
            }
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            return passwordProvider;
        }
    }
}
