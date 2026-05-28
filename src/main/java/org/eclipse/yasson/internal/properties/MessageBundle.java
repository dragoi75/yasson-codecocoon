/**
 * ****************************************************************************
 *  Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *
 *  Contributors:
 *      David Kral - initial implementation
 * ****************************************************************************
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
 *
 * @author David Kral
 */
public class MessageBundle {

    private final static String MESSAGES_BUNDLE = "yasson-messages";

    private final static String CHARSET = "UTF-8";

    static class UTF8ResourceBundleControl extends ResourceBundle.Control {

        public ResourceBundle newBundle(String resourceBase, Locale languageTag, String format, ClassLoader cl, boolean forceRefresh) throws IllegalAccessException, InstantiationException, IOException {
            // The below is a copy of the default implementation.
            String bundleBase = toBundleName(resourceBase, languageTag);
            String resourcePath = toResourceName(bundleBase, "properties");
            ResourceBundle rb = null;
            InputStream inputStreamRef = null;
            if (!forceRefresh) {
                inputStreamRef = cl.getResourceAsStream(resourcePath);
            } else {
                URL resourceUrl = cl.getResource(resourcePath);
                if (null != resourceUrl) {
                    URLConnection conn = resourceUrl.openConnection();
                    if (null != conn) {
                        conn.setUseCaches(false);
                        inputStreamRef = conn.getInputStream();
                    }
                }
            }
            if (null != inputStreamRef) {
                try {
                    // Only this line is changed to make it to read properties files as UTF-8.
                    rb = new PropertyResourceBundle(new InputStreamReader(inputStreamRef, CHARSET));
                } finally {
                    inputStreamRef.close();
                }
            }
            return rb;
        }
    }

    /**
     * ResourceBundle.Control is not supported when loaded from JPMS native module.
     */
    private static ResourceBundle getResourceBundle(Locale languageTag) {
        try {
            return ResourceBundle.getBundle(MESSAGES_BUNDLE, languageTag, new UTF8ResourceBundleControl());
        } catch (UnsupportedOperationException e) {
            return ResourceBundle.getBundle(MESSAGES_BUNDLE, languageTag);
        }
    }

    /**
     * Gets message by key and locale.
     *
     * @param messageId Message key.
     * @param languageTag Locale.
     * @param args Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageKey messageId, Locale languageTag, Object... args) {
        ResourceBundle resourceBundle = getResourceBundle(languageTag);
        MessageFormat messageFormatInstance = new MessageFormat(resourceBundle.getString(messageId.messageId));
        return messageFormatInstance.format(args);
    }

    /**
     * Gets message by key. Default locale is used.
     *
     * @param messageId Message key.
     * @param args Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageKey messageId, Object... args) {
        return getMessage(messageId, Locale.getDefault(), args);
    }

    private MessageBundle() {
    }

}
