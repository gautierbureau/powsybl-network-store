/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl.extensions;

import com.powsybl.commons.PowsyblException;
import com.powsybl.dynawo.extensions.api.generator.RpclType;
import com.powsybl.dynawo.extensions.api.generator.SynchronousGeneratorProperties;
import com.powsybl.dynawo.extensions.api.generator.SynchronousGeneratorPropertiesAdder;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.network.store.iidm.impl.DummyNetworkListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class SynchronousGeneratorPropertiesExtensionTest {

    private static final String GENERATOR_ID = "GEN";

    private static SynchronousGeneratorProperties addProperties(Generator generator) {
        return generator.newExtension(SynchronousGeneratorPropertiesAdder.class)
                .withNumberOfWindings(SynchronousGeneratorProperties.Windings.FOUR_WINDINGS)
                .withGovernor("Proportional")
                .withVoltageRegulator("Proportional")
                .withPss("")
                .withAuxiliaries(true)
                .withInternalTransformer(false)
                .withRpcl(RpclType.RPCL1)
                .withUva(SynchronousGeneratorProperties.Uva.DISTANT)
                .withAggregated(true)
                .withQlim(true)
                .add();
    }

    private static SynchronousGeneratorProperties getProperties(Network network) {
        return network.getGenerator(GENERATOR_ID).getExtension(SynchronousGeneratorProperties.class);
    }

    @Test
    void testExtension() {
        Network network = EurostagTutorialExample1Factory.create();
        Generator generator = network.getGenerator(GENERATOR_ID);
        assertNull(generator.getExtension(SynchronousGeneratorProperties.class));
        assertNull(generator.getExtensionByName(SynchronousGeneratorProperties.NAME));
        assertEquals(0, generator.getExtensions().size());

        addProperties(generator);

        SynchronousGeneratorProperties properties = getProperties(network);
        assertNotNull(properties);
        assertEquals(SynchronousGeneratorProperties.Windings.FOUR_WINDINGS, properties.getNumberOfWindings());
        assertEquals("Proportional", properties.getGovernor());
        assertEquals("Proportional", properties.getVoltageRegulator());
        assertEquals("", properties.getPss());
        assertTrue(properties.isAuxiliaries());
        assertFalse(properties.isInternalTransformer());
        assertEquals(RpclType.RPCL1, properties.getRpcl());
        assertTrue(properties.isRpcl1());
        assertFalse(properties.isRpcl2());
        assertEquals(SynchronousGeneratorProperties.Uva.DISTANT, properties.getUva());
        assertTrue(properties.isAggregated());
        assertTrue(properties.isQlim());
        assertNotNull(generator.getExtensionByName(SynchronousGeneratorProperties.NAME));
        assertEquals(1, generator.getExtensions().size());
    }

    @Test
    void testDefaultValues() {
        Generator generator = EurostagTutorialExample1Factory.create().getGenerator(GENERATOR_ID);
        SynchronousGeneratorProperties properties = generator.newExtension(SynchronousGeneratorPropertiesAdder.class)
                .withNumberOfWindings(SynchronousGeneratorProperties.Windings.THREE_WINDINGS)
                .add();
        assertEquals(SynchronousGeneratorProperties.Windings.THREE_WINDINGS, properties.getNumberOfWindings());
        assertNull(properties.getGovernor());
        assertNull(properties.getVoltageRegulator());
        assertNull(properties.getPss());
        assertFalse(properties.isAuxiliaries());
        assertFalse(properties.isInternalTransformer());
        assertEquals(RpclType.NONE, properties.getRpcl());
        assertEquals(SynchronousGeneratorProperties.Uva.LOCAL, properties.getUva());
        assertFalse(properties.isAggregated());
        assertFalse(properties.isQlim());
    }

    @Test
    void testAdderErrors() {
        Generator generator = EurostagTutorialExample1Factory.create().getGenerator(GENERATOR_ID);
        SynchronousGeneratorPropertiesAdder adder = generator.newExtension(SynchronousGeneratorPropertiesAdder.class);
        assertThrows(NullPointerException.class, adder::add);
        adder.withNumberOfWindings(SynchronousGeneratorProperties.Windings.FOUR_WINDINGS).withGovernor("Proportional");
        PowsyblException e = assertThrows(PowsyblException.class, adder::add);
        assertEquals("Governor and Voltage regulator must be both instantiated or both null", e.getMessage());
        assertThrows(NullPointerException.class, () -> adder.withRpcl(null));
        assertThrows(NullPointerException.class, () -> adder.withUva(null));
        assertNull(generator.getExtension(SynchronousGeneratorProperties.class));
    }

    @Test
    void testUpdate() {
        Network network = EurostagTutorialExample1Factory.create();
        DummyNetworkListener listener = new DummyNetworkListener();
        network.addListener(listener);
        SynchronousGeneratorProperties properties = addProperties(network.getGenerator(GENERATOR_ID));

        properties.setNumberOfWindings(SynchronousGeneratorProperties.Windings.THREE_WINDINGS);
        properties.setGovernor("ProportionalIntegral");
        properties.setVoltageRegulator("ProportionalIntegral");
        properties.setPss("Pss");
        properties.setAuxiliaries(false);
        properties.setInternalTransformer(true);
        properties.setRpcl(RpclType.RPCL2);
        properties.setUva(SynchronousGeneratorProperties.Uva.LOCAL);
        properties.setAggregated(false);
        properties.setQlim(false);
        assertEquals(10, listener.getNbUpdatedExtensions());

        // the values are those of the generator attributes, read by any instance of the extension
        SynchronousGeneratorProperties updated = getProperties(network);
        assertEquals(SynchronousGeneratorProperties.Windings.THREE_WINDINGS, updated.getNumberOfWindings());
        assertEquals("ProportionalIntegral", updated.getGovernor());
        assertEquals("ProportionalIntegral", updated.getVoltageRegulator());
        assertEquals("Pss", updated.getPss());
        assertFalse(updated.isAuxiliaries());
        assertTrue(updated.isInternalTransformer());
        assertEquals(RpclType.RPCL2, updated.getRpcl());
        assertFalse(updated.isRpcl1());
        assertTrue(updated.isRpcl2());
        assertEquals(SynchronousGeneratorProperties.Uva.LOCAL, updated.getUva());
        assertFalse(updated.isAggregated());
        assertFalse(updated.isQlim());

        // no notification for an unchanged value
        properties.setGovernor("ProportionalIntegral");
        properties.setQlim(false);
        assertEquals(10, listener.getNbUpdatedExtensions());

        assertThrows(NullPointerException.class, () -> properties.setNumberOfWindings(null));
    }

    @Test
    void testVariants() {
        Network network = EurostagTutorialExample1Factory.create();
        addProperties(network.getGenerator(GENERATOR_ID));
        VariantManager variantManager = network.getVariantManager();
        variantManager.cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "variant1");
        variantManager.setWorkingVariant("variant1");
        assertEquals("Proportional", getProperties(network).getGovernor());
        getProperties(network).setGovernor("ProportionalIntegral");
        getProperties(network).setRpcl(RpclType.NONE);

        variantManager.setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
        assertEquals("Proportional", getProperties(network).getGovernor());
        assertEquals(RpclType.RPCL1, getProperties(network).getRpcl());

        variantManager.setWorkingVariant("variant1");
        assertEquals("ProportionalIntegral", getProperties(network).getGovernor());
        assertEquals(RpclType.NONE, getProperties(network).getRpcl());
    }

    @Test
    void testRemove() {
        Generator generator = EurostagTutorialExample1Factory.create().getGenerator(GENERATOR_ID);
        addProperties(generator);
        assertTrue(generator.removeExtension(SynchronousGeneratorProperties.class));
        assertNull(generator.getExtension(SynchronousGeneratorProperties.class));
        assertEquals(0, generator.getExtensions().size());
        assertFalse(generator.removeExtension(SynchronousGeneratorProperties.class));
    }
}
