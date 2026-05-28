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
public class LocalizedMessages {

    private static final String LOCALIZED_MESSAGES_BUNDLE = "yasson-messages";

    private static final String CHARSET_NAME = "UTF-8";

    static class UTF8ResourceBundleControl extends ResourceBundle.Control {

        public ResourceBundle newBundle(String bundleBase, Locale targetRegion, String format, ClassLoader resourceClassLoader, boolean forceRefresh) throws IllegalAccessException, InstantiationException, IOException {
            // The below is a copy of the default implementation.
            String resourceBundleName = toBundleName(bundleBase, targetRegion);
            String resourcePath = toResourceName(resourceBundleName, "properties");
            ResourceBundle resultBundle = null;
            InputStream inputStreamRef = null;
            if (!forceRefresh) {
                inputStreamRef = resourceClassLoader.getResourceAsStream(resourcePath);
            } else {
                URL resourceUrl = resourceClassLoader.getResource(resourcePath);
                if (null != resourceUrl) {
                    URLConnection urlConn = resourceUrl.openConnection();
                    if (null != urlConn) {
                        urlConn.setUseCaches(false);
                        inputStreamRef = urlConn.getInputStream();
                    }
                }
            }
            if (null != inputStreamRef) {
                try {
                    // Only this line is changed to make it to read properties files as UTF-8.
                    resultBundle = new PropertyResourceBundle(new InputStreamReader(inputStreamRef, CHARSET_NAME));
                } finally {
                    inputStreamRef.close();
                }
            }
            return resultBundle;
        }
    }

    /**
     * Gets message by key and locale.
     *
     * @param messageIdentifier     Message key.
     * @param targetRegion  Locale.
     * @param formatArguments Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageKeyConstants messageIdentifier, Locale targetRegion, Object... formatArguments) {
        ResourceBundle resourceBundle = getResourceBundle(targetRegion);
        MessageFormat messageFormatInstance = new MessageFormat(resourceBundle.getString(messageIdentifier.getKey()));
        return messageFormatInstance.format(formatArguments);
    }

    /**
     * ResourceBundle.Control is not supported when loaded from JPMS native module.
     */
    private static ResourceBundle getResourceBundle(Locale targetRegion) {
        try {
            return ResourceBundle.getBundle(LOCALIZED_MESSAGES_BUNDLE, targetRegion, new UTF8ResourceBundleControl());
        } catch (UnsupportedOperationException e) {
            return ResourceBundle.getBundle(LOCALIZED_MESSAGES_BUNDLE, targetRegion);
        }
    }

    /**
     * Gets message by key. Default locale is used.
     *
     * @param messageIdentifier     Message key.
     * @param formatArguments Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageKeyConstants messageIdentifier, Object... formatArguments) {
        return getMessage(messageIdentifier, Locale.getDefault(), formatArguments);
    }

    private LocalizedMessages() {
    }

}
