/*******************************************************************************
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *     David Kral - initial implementation
 ******************************************************************************/
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
 *
 * @author David Kral
 */
public class ResourceBundleMessages {

    private final static String MESSAGE_RESOURCE = "yasson-messages";
    private final static String CHARSET = "UTF-8";

    private ResourceBundleMessages() {
    }

    /**
     * Gets message by key. Default locale is used.
     *
     * @param messageCode Message key.
     * @param args Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageConstants messageCode, Object... args) {
        return getMessage(messageCode, Locale.getDefault(), args);
    }

    /**
     * Gets message by key and locale.
     *
     * @param messageCode Message key.
     * @param targetRegion Locale.
     * @param args Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageConstants messageCode, Locale targetRegion, Object... args) {
        ResourceBundle resourceBundle = getResourceBundle(targetRegion);
        MessageFormat messageFormat = new MessageFormat(resourceBundle.getString(messageCode.messageId));
        return messageFormat.format(args);
    }

    /**
     * ResourceBundle.Control is not supported when loaded from JPMS native module.
     */
    private static ResourceBundle getResourceBundle(Locale targetRegion) {
        try {
            return ResourceBundle.getBundle(MESSAGE_RESOURCE, targetRegion, new UTF8ResourceControl());
        } catch (UnsupportedOperationException e) {
            return ResourceBundle.getBundle(MESSAGE_RESOURCE, targetRegion);
        }
    }

    static class UTF8ResourceControl extends ResourceBundle.Control {
        public ResourceBundle newBundle
                (String resourceBase, Locale targetRegion, String format, ClassLoader cl, boolean refresh)
                throws IllegalAccessException, InstantiationException, IOException
        {
            // The below is a copy of the default implementation.
            String bundleBase = toBundleName(resourceBase, targetRegion);
            String resourcePath = toResourceName(bundleBase, "properties");
            ResourceBundle resources = null;
            InputStream in = null;
            if (refresh) {
                URL location = cl.getResource(resourcePath);
                if (location != null) {
                    URLConnection conn = location.openConnection();
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
                    resources = new PropertyResourceBundle(new InputStreamReader(in, CHARSET));
                } finally {
                    in.close();
                }
            }
            return resources;
        }
    }

}
