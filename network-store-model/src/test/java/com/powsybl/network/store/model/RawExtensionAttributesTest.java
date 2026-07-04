/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * An extension unknown on this side (no loader, e.g. sent by a more recent client) must round trip
 * unchanged: it used to deserialize to {@link RawExtensionAttributes} but fail on the way back
 * because the extension name was lost.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class RawExtensionAttributesTest {

    @Test
    void testUnknownExtensionRoundTrip() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = "{\"extensionName\":\"someFutureExtension\",\"value\":3.0,\"refs\":[{\"connectableId\":\"GEN\",\"side\":null}]}";

        ExtensionAttributes attributes = objectMapper.readValue(json, ExtensionAttributes.class);
        RawExtensionAttributes rawAttributes = assertInstanceOf(RawExtensionAttributes.class, attributes);
        assertEquals("someFutureExtension", rawAttributes.getExtensionName());
        assertEquals(3.0, rawAttributes.getAttributes().get("value"));

        String serialized = objectMapper.writeValueAsString(attributes);
        assertEquals(objectMapper.readTree(json), objectMapper.readTree(serialized));

        // and a second round trip is stable too
        ExtensionAttributes reread = objectMapper.readValue(serialized, ExtensionAttributes.class);
        assertEquals(objectMapper.readTree(json), objectMapper.readTree(objectMapper.writeValueAsString(reread)));
    }
}
