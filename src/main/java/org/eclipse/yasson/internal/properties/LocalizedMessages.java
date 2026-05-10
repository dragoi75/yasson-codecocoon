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
public class LocalizedMessages {

    private final static String MESSAGES_BASE_NAME = "yasson-messages";
    private final static String DEFAULT_CHARSET = "UTF-8";

    private LocalizedMessages() {
    }

    /**
     * Gets message by key. Default locale is used.
     *
     * @param messageId Message key.
     * @param args Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageConstants messageId, Object... args) {
        return getMessage(messageId, Locale.getDefault(), args);
    }

    /**
     * Gets message by key and locale.
     *
     * @param messageId Message key.
     * @param targetLocale Locale.
     * @param args Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageConstants messageId, Locale targetLocale, Object... args) {
        ResourceBundle resourceBundle = getResourceBundle(targetLocale);
        MessageFormat messageFormat = new MessageFormat(resourceBundle.getString(messageId.messageIdentifier));
        return messageFormat.format(args);
    }

    /**
     * ResourceBundle.Control is not supported when loaded from JPMS native module.
     */
    private static ResourceBundle getResourceBundle(Locale targetLocale) {
        try {
            return ResourceBundle.getBundle(MESSAGES_BASE_NAME, targetLocale, new UTF8ResourceBundleControl());
        } catch (UnsupportedOperationException e) {
            return ResourceBundle.getBundle(MESSAGES_BASE_NAME, targetLocale);
        }
    }

    static class UTF8ResourceBundleControl extends ResourceBundle.Control {
        public ResourceBundle newBundle
                (String bundleBaseName, Locale targetLocale, String format, ClassLoader classLoader, boolean shouldReload)
                throws IllegalAccessException, InstantiationException, IOException
        {
            // The below is a copy of the default implementation.
            String resolvedBundleName = toBundleName(bundleBaseName, targetLocale);
            String resourcePath = toResourceName(resolvedBundleName, "properties");
            ResourceBundle resourceBundle = null;
            InputStream inputStream = null;
            if (shouldReload) {
                URL resourceUrl = classLoader.getResource(resourcePath);
                if (resourceUrl != null) {
                    URLConnection urlConnection = resourceUrl.openConnection();
                    if (urlConnection != null) {
                        urlConnection.setUseCaches(false);
                        inputStream = urlConnection.getInputStream();
                    }
                }
            } else {
                inputStream = classLoader.getResourceAsStream(resourcePath);
            }
            if (inputStream != null) {
                try {
                    // Only this line is changed to make it to read properties files as UTF-8.
                    resourceBundle = new PropertyResourceBundle(new InputStreamReader(inputStream, DEFAULT_CHARSET));
                } finally {
                    inputStream.close();
                }
            }
            return resourceBundle;
        }
    }

}
