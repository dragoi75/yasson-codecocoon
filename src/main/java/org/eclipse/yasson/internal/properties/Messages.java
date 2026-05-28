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
public class Messages {

    private final static String MESSAGE_BUNDLE = "yasson-messages";

    private final static String ENCODING = "UTF-8";

    static class UTF8Control extends ResourceBundle.Control {

        public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload) throws IllegalAccessException, InstantiationException, IOException {
            // The below is a copy of the default implementation.
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            ResourceBundle bundle = null;
            InputStream stream = null;
            if (!reload) {
                stream = loader.getResourceAsStream(resourceName);
            } else {
                URL url = loader.getResource(resourceName);
                if (null != url) {
                    URLConnection connection = url.openConnection();
                    if (null != connection) {
                        connection.setUseCaches(false);
                        stream = connection.getInputStream();
                    }
                }
            }
            if (null != stream) {
                try {
                    // Only this line is changed to make it to read properties files as UTF-8.
                    bundle = new PropertyResourceBundle(new InputStreamReader(stream, ENCODING));
                } finally {
                    stream.close();
                }
            }
            return bundle;
        }
    }

    /**
     * Gets message by key and locale.
     *
     * @param key Message key.
     * @param locale Locale.
     * @param objects Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageKeyConstants key, Locale locale, Object... objects) {
        ResourceBundle messages = getResourceBundle(locale);
        MessageFormat formatter = new MessageFormat(messages.getString(key.messageIdentifier));
        return formatter.format(objects);
    }

    /**
     * ResourceBundle.Control is not supported when loaded from JPMS native module.
     */
    private static ResourceBundle getResourceBundle(Locale locale) {
        try {
            return ResourceBundle.getBundle(MESSAGE_BUNDLE, locale, new UTF8Control());
        } catch (UnsupportedOperationException e) {
            return ResourceBundle.getBundle(MESSAGE_BUNDLE, locale);
        }
    }

    private Messages() {
    }

    /**
     * Gets message by key. Default locale is used.
     *
     * @param key Message key.
     * @param objects Message parameters.
     * @return Formatted message in string.
     */
    public static String getMessage(MessageKeyConstants key, Object... objects) {
        return getMessage(key, Locale.getDefault(), objects);
    }

}
