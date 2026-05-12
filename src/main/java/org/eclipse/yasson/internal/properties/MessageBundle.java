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

    private static final String RESOURCE_STRINGS = "yasson-messages";
    private static final String CHARSET_NAME = "UTF-8";

    private MessageBundle() {
    }

    /**
     * Gets message by key. Default locale is used.
     *
     * @param messageIdentifier     Message key.
     * @param args Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageKeyConstants messageIdentifier, Object... args) {
        return getMessage(messageIdentifier, Locale.getDefault(), args);
    }

    /**
     * Gets message by key and locale.
     *
     * @param messageIdentifier     Message key.
     * @param targetRegion  Locale.
     * @param args Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageKeyConstants messageIdentifier, Locale targetRegion, Object... args) {
        ResourceBundle resources = getResourceBundle(targetRegion);
        MessageFormat messageFormat = new MessageFormat(resources.getString(messageIdentifier.getKey()));
        return messageFormat.format(args);
    }

    /**
     * ResourceBundle.Control is not supported when loaded from JPMS native module.
     */
    private static ResourceBundle getResourceBundle(Locale targetRegion) {
        try {
            return ResourceBundle.getBundle(RESOURCE_STRINGS, targetRegion, new UTF8ResourceBundleControl());
        } catch (UnsupportedOperationException e) {
            return ResourceBundle.getBundle(RESOURCE_STRINGS, targetRegion);
        }
    }

    static class UTF8ResourceBundleControl extends ResourceBundle.Control {
        public ResourceBundle newBundle(String bundleBase, Locale targetRegion, String format, ClassLoader classLoaderRef, boolean forceReload)
                throws IllegalAccessException, InstantiationException, IOException {
            // The below is a copy of the default implementation.
            String bundleBaseName = toBundleName(bundleBase, targetRegion);
            String resourcePath = toResourceName(bundleBaseName, "properties");
            ResourceBundle resourceSet = null;
            InputStream inputStream = null;
            if (forceReload) {
                URL resourceUrl = classLoaderRef.getResource(resourcePath);
                if (resourceUrl != null) {
                    URLConnection urlConnection = resourceUrl.openConnection();
                    if (urlConnection != null) {
                        urlConnection.setUseCaches(false);
                        inputStream = urlConnection.getInputStream();
                    }
                }
            } else {
                inputStream = classLoaderRef.getResourceAsStream(resourcePath);
            }
            if (inputStream != null) {
                try {
                    // Only this line is changed to make it to read properties files as UTF-8.
                    resourceSet = new PropertyResourceBundle(new InputStreamReader(inputStream, CHARSET_NAME));
                } finally {
                    inputStream.close();
                }
            }
            return resourceSet;
        }
    }

}
