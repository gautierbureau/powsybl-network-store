/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

/**
 * @author Antoine Bouhours <antoine.bouhours at rte-france.com>
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "extensionName",
        // the type property is exposed to the deserializers so that an extension unknown on this
        // side (version skew between client and server) keeps its name in RawExtensionAttributes
        // and can be serialized back: without it the store cannot round trip such an extension
        visible = true
)
@JsonTypeIdResolver(ExtensionAttributesIdResolver.class)
@JsonIgnoreProperties("extensionName")
public interface ExtensionAttributes {
    // This property is used to not persist some extensions that are only used at import/export.
    @JsonIgnore
    default boolean isPersistent() {
        return true;
    }
}
