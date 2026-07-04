/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class AttributesCopierTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static Resource<GeneratorAttributes> createGeneratorResource() {
        // no extension attributes here: their jackson (de)serialization needs the extension loaders,
        // registered in the iidm-impl module; they are covered by StructuralCloneDifferentialTest
        List<ReactiveCapabilityCurvePointAttributes> points = new ArrayList<>();
        points.add(ReactiveCapabilityCurvePointAttributes.builder().p(10).minQ(-5).maxQ(5).build());
        points.add(ReactiveCapabilityCurvePointAttributes.builder().p(20).minQ(-10).maxQ(10).build());
        TreeMap<Double, ReactiveCapabilityCurvePointAttributes> curve = new TreeMap<>();
        points.forEach(point -> curve.put(point.getP(), point));
        Map<String, String> properties = new HashMap<>();
        properties.put("key", "value");
        return Resource.generatorBuilder()
            .id("gen1")
            .variantNum(0)
            .attributes(GeneratorAttributes.builder()
                .name("gen1")
                .voltageLevelId("vl1")
                .bus("bus1")
                .minP(0)
                .maxP(100)
                .targetP(50)
                .targetV(400)
                .properties(properties)
                .reactiveLimits(ReactiveCapabilityCurveAttributes.builder().points(curve).build())
                .build())
            .build();
    }

    @Test
    void deepCopyIsIndependentFromTheSource() {
        Resource<GeneratorAttributes> resource = createGeneratorResource();

        List<Resource<GeneratorAttributes>> cloned = Resource.cloneResourcesToVariant(List.of(resource), 3, objectMapper, null);
        assertEquals(1, cloned.size());
        Resource<GeneratorAttributes> clonedResource = cloned.get(0);

        // the clone carries the new variant number and its own back reference
        assertEquals(3, clonedResource.getVariantNum());
        assertEquals(0, resource.getVariantNum());
        assertSame(clonedResource, clonedResource.getAttributes().getResource());

        // same state...
        assertEquals("gen1", clonedResource.getAttributes().getName());
        assertEquals(50, clonedResource.getAttributes().getTargetP(), 0);
        assertEquals("value", clonedResource.getAttributes().getProperties().get("key"));
        ReactiveCapabilityCurveAttributes curve = (ReactiveCapabilityCurveAttributes) clonedResource.getAttributes().getReactiveLimits();
        assertEquals(2, curve.getPoints().size());

        // ...but fully independent: mutating the source does not touch the clone
        resource.getAttributes().setTargetP(99);
        resource.getAttributes().getProperties().put("key", "otherValue");
        ((ReactiveCapabilityCurveAttributes) resource.getAttributes().getReactiveLimits()).getPoints().clear();

        assertEquals(50, clonedResource.getAttributes().getTargetP(), 0);
        assertEquals("value", clonedResource.getAttributes().getProperties().get("key"));
        assertEquals(2, curve.getPoints().size());
        assertNotSame(resource.getAttributes(), clonedResource.getAttributes());
    }

    @Test
    void nullCollectionsStayNull() {
        // the previous jackson clone kept absent fields null: the structural copy must not replace
        // them with defaults
        Resource<LoadAttributes> resource = Resource.loadBuilder()
            .id("load1")
            .variantNum(0)
            .attributes(LoadAttributes.builder()
                .voltageLevelId("vl1")
                .p0(10)
                .build())
            .build();
        resource.getAttributes().setExtensionAttributes(null);
        resource.getAttributes().setProperties(null);

        Resource<LoadAttributes> clonedResource = Resource.cloneResourcesToVariant(List.of(resource), 1, objectMapper, null).get(0);
        assertNull(clonedResource.getAttributes().getExtensionAttributes());
        assertNull(clonedResource.getAttributes().getProperties());
    }
}
