/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal.model.customization;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import jakarta.json.bind.annotation.JsonbTypeInfo;

/**
 * Type inheritance configuration.
 */
public class TypeInheritanceSettings {

    private final String propertyName;
    private final boolean inheritsFromParent;
    private final Map<Class<?>, String> aliasMap;
    private final Class<?> explicitType;
    private final TypeInheritanceSettings parentSettings;

    public static final class FieldConfigBuilder {

        private Map<Class<?>, String> aliasMap = new HashMap<>();
        private String propertyName = JsonbTypeInfo.DEFAULT_KEY_NAME;
        private boolean inheritsFromParent = false;
        private Class<?> explicitType;
        private TypeInheritanceSettings parentSettings;

        public TypeInheritanceSettings create() {
            return new TypeInheritanceSettings(this);
        }

        public FieldConfigBuilder addAlias(Class<?> targetType, String alternateName) {
            this.aliasMap.put(targetType, alternateName);
            return this;
        }

        public FieldConfigBuilder from(TypeInheritanceSettings inheritanceConfig) {
            this.propertyName = inheritanceConfig.propertyName;
            this.aliasMap = new HashMap<>(inheritanceConfig.aliasMap);
            this.inheritsFromParent = inheritanceConfig.inheritsFromParent;
            this.parentSettings = inheritanceConfig.parentSettings;
            this.explicitType = inheritanceConfig.explicitType;
            return this;
        }

        public FieldConfigBuilder setDefinedType(Class<?> explicitType) {
            this.explicitType = explicitType;
            return this;
        }

        public FieldConfigBuilder setParentConfig(TypeInheritanceSettings parentSettings) {
            this.parentSettings = parentSettings;
            return this;
        }

        public FieldConfigBuilder setFieldName(String propertyName) {
            this.propertyName = Objects.requireNonNull(propertyName);
            return this;
        }

        private FieldConfigBuilder() {
        }

        public FieldConfigBuilder setInherited(boolean inheritsFromParent) {
            this.inheritsFromParent = inheritsFromParent;
            return this;
        }

    }

    public TypeInheritanceSettings getParentConfig() {
        return parentSettings;
    }

    public boolean isInherited() {
        return inheritsFromParent;
    }

    public Class<?> getDefinedType() {
        return explicitType;
    }

    public static FieldConfigBuilder newBuilder() {
        return new FieldConfigBuilder();
    }

    public String getFieldName() {
        return propertyName;
    }

    private TypeInheritanceSettings(FieldConfigBuilder configCreator) {
        this.propertyName = configCreator.propertyName;
        this.inheritsFromParent = configCreator.inheritsFromParent;
        this.aliasMap = Map.copyOf(configCreator.aliasMap);
        this.parentSettings = configCreator.parentSettings;
        this.explicitType = configCreator.explicitType;
    }

    public Map<Class<?>, String> getAliases() {
        return aliasMap;
    }

}
