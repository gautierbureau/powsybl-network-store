/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.powsybl.cgmes.conformity.CgmesConformity1Catalog;
import com.powsybl.commons.json.JsonUtil;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.network.store.model.IdentifiableAttributes;
import com.powsybl.network.store.model.Resource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Differential test of the structural resource copy: for every resource of real networks, cloning
 * through the structural copier must give exactly the same persisted state as the previous Jackson
 * round-trip clone. The comparison is on the canonical json of the cloned resources (the json view
 * is the persisted state; the resource back reference is json ignored on purpose).
 */
class StructuralCloneDifferentialTest {

    // same mapper construction as the clone call sites (CachedNetworkStoreClient, BufferedNetworkStoreClient)
    private static final ObjectMapper MAPPER = JsonUtil.createObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
        .configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);

    /**
     * The clone implementation this differential test guards against: a Jackson round trip.
     */
    private static Resource<IdentifiableAttributes> jacksonClone(Resource<IdentifiableAttributes> resource, int newVariantNum) {
        try (TokenBuffer buffer = new TokenBuffer(MAPPER, false)) {
            MAPPER.writeValue(buffer, resource);
            Resource<IdentifiableAttributes> clonedResource = MAPPER.readValue(buffer.asParser(), new TypeReference<>() {
            });
            clonedResource.setVariantNum(newVariantNum);
            return clonedResource;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkAllResources(Network network) throws IOException {
        NetworkImpl networkImpl = (NetworkImpl) network;
        List<Resource<IdentifiableAttributes>> resources = new ArrayList<>();
        resources.add((Resource<IdentifiableAttributes>) (Resource<?>) networkImpl.getResource());
        for (Identifiable<?> identifiable : network.getIdentifiables()) {
            if (identifiable instanceof AbstractIdentifiableImpl<?, ?> identifiableImpl) {
                resources.add((Resource<IdentifiableAttributes>) identifiableImpl.getResource());
            }
        }
        assertTrue(resources.size() > 10);

        for (Resource<IdentifiableAttributes> resource : resources) {
            Resource<IdentifiableAttributes> expected = jacksonClone(resource, 12);
            List<Resource<IdentifiableAttributes>> cloned = Resource.cloneResourcesToVariant(List.of(resource), 12, MAPPER, null);
            assertEquals(1, cloned.size());
            Resource<IdentifiableAttributes> actual = cloned.get(0);
            assertEquals(MAPPER.writeValueAsString(expected), MAPPER.writeValueAsString(actual),
                "structural clone differs from jackson clone for " + resource.getType() + " " + resource.getId());
            assertSame(actual, actual.getAttributes().getResource());
            assertEquals(12, actual.getVariantNum());
        }
    }

    @Test
    void sameCloneAsJacksonOnCgmesSmallGrid() throws IOException {
        checkAllResources(Network.read(CgmesConformity1Catalog.smallBusBranch().dataSource()));
    }

    @Test
    void sameCloneAsJacksonOnCgmesMicroGrid() throws IOException {
        // node/breaker network with switches, 3 windings transformers, tie lines, tap changers and cgmes extensions
        checkAllResources(Network.read(CgmesConformity1Catalog.microGridBaseCaseBE().dataSource()));
    }

    @Test
    void sameCloneAsJacksonOnEurostagWithExtensions() throws IOException {
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();
        Generator generator = network.getGenerator("GEN");
        generator.newExtension(ActivePowerControlAdder.class)
            .withParticipate(true)
            .withDroop(4)
            .withParticipationFactor(1.5)
            .add();
        generator.setProperty("testProperty", "testValue");
        checkAllResources(network);
    }
}
