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

    private static final String MESSAGE_RESOURCE = "yasson-messages";
    private static final String CHARSET = "UTF-8";

    private LocalizedMessages() {
    }

    /**
     * Gets message by key. Default locale is used.
     *
     * @param messageId     Message key.
     * @param args Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageKeyConstants messageId, Object... args) {
        return getMessage(messageId, Locale.getDefault(), args);
    }

    /**
     * Gets message by key and locale.
     *
     * @param messageId     Message key.
     * @param region  Locale.
     * @param args Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageKeyConstants messageId, Locale region, Object... args) {
        ResourceBundle bundle = getResourceBundle(region);
        MessageFormat messageFormat = new MessageFormat(bundle.getString(messageId.getKey()));
        return messageFormat.format(args);
    }

    /**
     * ResourceBundle.Control is not supported when loaded from JPMS native module.
     */
    private static ResourceBundle getResourceBundle(Locale region) {
        try {
            return ResourceBundle.getBundle(MESSAGE_RESOURCE, region, new UTF8ResourceControl());
        } catch (UnsupportedOperationException e) {
            return ResourceBundle.getBundle(MESSAGE_RESOURCE, region);
        }
    }

    static class UTF8ResourceControl extends ResourceBundle.Control {
        public ResourceBundle newBundle(String bundleBase, Locale region, String format, ClassLoader cl, boolean shouldRefresh)
                throws IllegalAccessException, InstantiationException, IOException {
            // The below is a copy of the default implementation.
            String bundleId = toBundleName(bundleBase, region);
            String resourcePath = toResourceName(bundleId, "properties");
            ResourceBundle rb = null;
            InputStream in = null;
            if (shouldRefresh) {
                URL resourceLocation = cl.getResource(resourcePath);
                if (resourceLocation != null) {
                    URLConnection conn = resourceLocation.openConnection();
                    if (conn != null) {
                        conn.setUseCaches(false);
                        in = conn.getInputStream();
                    }
                }
            } else {
                in = cl.getResourceAsStream(resourcePath);
            }
            if (in != null) {
                try {
                    // Only this line is changed to make it to read properties files as UTF-8.
                    rb = new PropertyResourceBundle(new InputStreamReader(in, CHARSET));
                } finally {
                    in.close();
                }
            }
            return rb;
        }
    }

}
