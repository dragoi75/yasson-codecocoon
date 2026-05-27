/*
 * Copyright (c) 2015, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */
package org.eclipse.yasson.internal.properties;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * JSON-B messages.
 */
public class MessageBundle {

    private static final String I18N_BASE_NAME = "yasson-messages";

    private static final String CHARACTER_ENCODING = "UTF-8";

    static class UTF8ResourceBundleControl extends ResourceBundle.Control {

        public ResourceBundle newBundle(String baseIdentifier, Locale languageTag, String format, ClassLoader classProvider, boolean refreshNeeded) throws IllegalAccessException, InstantiationException, IOException {
            // The below is a copy of the default implementation.
            String bundleBase = toBundleName(baseIdentifier, languageTag);
            String resourcePath = toResourceName(bundleBase, "properties");
            ResourceBundle resourceBundle = null;
            InputStream inputStream = null;
            if (!refreshNeeded) {
                inputStream = classProvider.getResourceAsStream(resourcePath);
            } else {
                URL resourceUrl = classProvider.getResource(resourcePath);
                if (null != resourceUrl) {
                    URLConnection urlConnection = resourceUrl.openConnection();
                    if (null != urlConnection) {
                        urlConnection.setUseCaches(false);
                        inputStream = urlConnection.getInputStream();
                    }
                }
            }
            if (null != inputStream) {
                try {
                    // Only this line is changed to make it to read properties files as UTF-8.
                    resourceBundle = new PropertyResourceBundle(new InputStreamReader(inputStream, CHARACTER_ENCODING));
                } finally {
                    inputStream.close();
                }
            }
            return resourceBundle;
        }
    }

    /**
     * ResourceBundle.Control is not supported when loaded from JPMS native module.
     */
    private static ResourceBundle getResourceBundle(Locale languageTag) {
        try {
            return ResourceBundle.getBundle(I18N_BASE_NAME, languageTag, new UTF8ResourceBundleControl());
        } catch (UnsupportedOperationException e) {
            return ResourceBundle.getBundle(I18N_BASE_NAME, languageTag);
        }
    }

    /**
     * Gets message by key and locale.
     *
     * @param messageId     Message key.
     * @param languageTag  Locale.
     * @param args Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageKeysEnum messageId, Locale languageTag, Object... args) {
        ResourceBundle resourceBundle = getResourceBundle(languageTag);
        MessageFormat messageFormat = new MessageFormat(resourceBundle.getString(messageId.getKey()));
        return messageFormat.format(args);
    }

    /**
     * Gets message by key. Default locale is used.
     *
     * @param messageId     Message key.
     * @param args Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageKeysEnum messageId, Object... args) {
        return getMessage(messageId, Locale.getDefault(), args);
    }

    private MessageBundle() {
    }

}
