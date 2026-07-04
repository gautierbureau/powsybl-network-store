/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Map;

/**
 * Attributes of an extension that has no {@link ExtensionLoader} on this side (typically a version
 * skew between client and server): the body is kept as a raw map and the extension name is
 * retained so that the extension can be serialized back unchanged (see
 * {@link ExtensionAttributesIdResolver}).
 *
 * @author Antoine Bouhours <antoine.bouhours at rte-france.com>
 */
@JsonDeserialize(using = RawExtensionAttributesDeserializer.class)
public class RawExtensionAttributes implements ExtensionAttributes {

    @JsonIgnore
    private String extensionName;

    private Map<String, Object> attributes;

    public RawExtensionAttributes() {
    }

    public RawExtensionAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public RawExtensionAttributes(String extensionName, Map<String, Object> attributes) {
        this.extensionName = extensionName;
        this.attributes = attributes;
    }

    public String getExtensionName() {
        return extensionName;
    }

    /**
     * Serialized back as top level properties so that the extension body round trips unchanged.
     */
    @JsonAnyGetter
    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
