/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl;

import com.powsybl.network.store.model.ExtensionAttributes;

import java.util.List;
import java.util.Objects;

/**
 * An extension attributes class unknown to the model module, mimicking a third-party plugin
 * extension: it must be copied by the Jackson fallback of the structural copier.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class PluginTestExtensionAttributes implements ExtensionAttributes {

    private String pluginField;

    private List<String> values;

    public String getPluginField() {
        return pluginField;
    }

    public void setPluginField(String pluginField) {
        this.pluginField = pluginField;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PluginTestExtensionAttributes other
            && Objects.equals(pluginField, other.pluginField)
            && Objects.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pluginField, values);
    }
}
