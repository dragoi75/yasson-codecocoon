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
public class MessageProvider {

    private static final String MESSAGES_BASE_NAME = "yasson-messages";

    private static final String DEFAULT_CHARSET = "UTF-8";

    static class UTF8ResourceBundleControl extends ResourceBundle.Control {

        public ResourceBundle newBundle(String bundleBase, Locale userRegion, String format, ClassLoader classProvider, boolean shouldRefresh) throws IllegalAccessException, InstantiationException, IOException {
            // The below is a copy of the default implementation.
            String baseBundleId = toBundleName(bundleBase, userRegion);
            String resourcePath = toResourceName(baseBundleId, "properties");
            ResourceBundle resourceSet = null;
            InputStream input = null;
            if (!shouldRefresh) {
                input = classProvider.getResourceAsStream(resourcePath);
            } else {
                URL resourceLocation = classProvider.getResource(resourcePath);
                if (null != resourceLocation) {
                    URLConnection urlConn = resourceLocation.openConnection();
                    if (null != urlConn) {
                        urlConn.setUseCaches(false);
                        input = urlConn.getInputStream();
                    }
                }
            }
            if (null != input) {
                try {
                    // Only this line is changed to make it to read properties files as UTF-8.
                    resourceSet = new PropertyResourceBundle(new InputStreamReader(input, DEFAULT_CHARSET));
                } finally {
                    input.close();
                }
            }
            return resourceSet;
        }
    }

    /**
     * Gets message by key and locale.
     *
     * @param messageCode     Message key.
     * @param userRegion  Locale.
     * @param args Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageConstants messageCode, Locale userRegion, Object... args) {
        ResourceBundle resourceBundle = getResourceBundle(userRegion);
        MessageFormat messageFormat = new MessageFormat(resourceBundle.getString(messageCode.getKey()));
        return messageFormat.format(args);
    }

    private MessageProvider() {
    }

    /**
     * ResourceBundle.Control is not supported when loaded from JPMS native module.
     */
    private static ResourceBundle getResourceBundle(Locale userRegion) {
        try {
            return ResourceBundle.getBundle(MESSAGES_BASE_NAME, userRegion, new UTF8ResourceBundleControl());
        } catch (UnsupportedOperationException e) {
            return ResourceBundle.getBundle(MESSAGES_BASE_NAME, userRegion);
        }
    }

    /**
     * Gets message by key. Default locale is used.
     *
     * @param messageCode     Message key.
     * @param args Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageConstants messageCode, Object... args) {
        return getMessage(messageCode, Locale.getDefault(), args);
    }

}
