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

    private final static String LOCALIZED_RESOURCE_BASENAME = "yasson-messages";
    private final static String DEFAULT_CHARSET = "UTF-8";

    private LocalizedMessages() {
    }

    /**
     * Gets message by key. Default locale is used.
     *
     * @param messageId Message key.
     * @param formatArgs Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageKeyConstants messageId, Object... formatArgs) {
        return getMessage(messageId, Locale.getDefault(), formatArgs);
    }

    /**
     * Gets message by key and locale.
     *
     * @param messageId Message key.
     * @param languageTag Locale.
     * @param formatArgs Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageKeyConstants messageId, Locale languageTag, Object... formatArgs) {
        ResourceBundle resourceBundle = getResourceBundle(languageTag);
        MessageFormat patternFormatter = new MessageFormat(resourceBundle.getString(messageId.messageId));
        return patternFormatter.format(formatArgs);
    }

    /**
     * ResourceBundle.Control is not supported when loaded from JPMS native module.
     */
    private static ResourceBundle getResourceBundle(Locale languageTag) {
        try {
            return ResourceBundle.getBundle(LOCALIZED_RESOURCE_BASENAME, languageTag, new UTF8ResourceBundleControl());
        } catch (UnsupportedOperationException e) {
            return ResourceBundle.getBundle(LOCALIZED_RESOURCE_BASENAME, languageTag);
        }
    }

    static class UTF8ResourceBundleControl extends ResourceBundle.Control {
        public ResourceBundle newBundle
                (String rootName, Locale languageTag, String format, ClassLoader clProvider, boolean shouldRefresh)
                throws IllegalAccessException, InstantiationException, IOException
        {
            // The below is a copy of the default implementation.
            String qualifiedName = toBundleName(rootName, languageTag);
            String resourcePath = toResourceName(qualifiedName, "properties");
            ResourceBundle resourceBundle = null;
            InputStream inputStream = null;
            if (shouldRefresh) {
                URL resourceLocation = clProvider.getResource(resourcePath);
                if (resourceLocation != null) {
                    URLConnection resourceConnection = resourceLocation.openConnection();
                    if (resourceConnection != null) {
                        resourceConnection.setUseCaches(false);
                        inputStream = resourceConnection.getInputStream();
                    }
                }
            } else {
                inputStream = clProvider.getResourceAsStream(resourcePath);
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
