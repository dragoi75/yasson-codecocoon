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

    private static final String I18N_KEYS = "yasson-messages";

    private static final String CHARSET = "UTF-8";

    static class UTF8ResourceBundleControl extends ResourceBundle.Control {

        public ResourceBundle newBundle(String resourceBase, Locale region, String format, ClassLoader classProvider, boolean refresh) throws IllegalAccessException, InstantiationException, IOException {
            // The below is a copy of the default implementation.
            String resourceNameBase = toBundleName(resourceBase, region);
            String resourcePath = toResourceName(resourceNameBase, "properties");
            ResourceBundle resources = null;
            InputStream input = null;
            if (!refresh) {
                input = classProvider.getResourceAsStream(resourcePath);
            } else {
                URL resourceLocation = classProvider.getResource(resourcePath);
                if (null != resourceLocation) {
                    URLConnection networkLink = resourceLocation.openConnection();
                    if (null != networkLink) {
                        networkLink.setUseCaches(false);
                        input = networkLink.getInputStream();
                    }
                }
            }
            if (null != input) {
                try {
                    // Only this line is changed to make it to read properties files as UTF-8.
                    resources = new PropertyResourceBundle(new InputStreamReader(input, CHARSET));
                } finally {
                    input.close();
                }
            }
            return resources;
        }
    }

    /**
     * ResourceBundle.Control is not supported when loaded from JPMS native module.
     */
    private static ResourceBundle getResourceBundle(Locale region) {
        try {
            return ResourceBundle.getBundle(I18N_KEYS, region, new UTF8ResourceBundleControl());
        } catch (UnsupportedOperationException e) {
            return ResourceBundle.getBundle(I18N_KEYS, region);
        }
    }

    /**
     * Gets message by key and locale.
     *
     * @param errorCode     Message key.
     * @param region  Locale.
     * @param args Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(ErrorMessageKeys errorCode, Locale region, Object... args) {
        ResourceBundle resources = getResourceBundle(region);
        MessageFormat messageFormat = new MessageFormat(resources.getString(errorCode.getKey()));
        return messageFormat.format(args);
    }

    /**
     * Gets message by key. Default locale is used.
     *
     * @param errorCode     Message key.
     * @param args Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(ErrorMessageKeys errorCode, Object... args) {
        return getMessage(errorCode, Locale.getDefault(), args);
    }

    private MessageBundle() {
    }

}
