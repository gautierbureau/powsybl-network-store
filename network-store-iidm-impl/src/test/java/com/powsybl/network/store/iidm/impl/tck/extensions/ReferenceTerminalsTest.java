/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.network.store.iidm.impl.tck.extensions;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.iidm.network.extensions.ReferenceTerminals;
import com.powsybl.iidm.network.extensions.ReferenceTerminalsAdder;
import com.powsybl.iidm.network.tck.extensions.AbstractReferenceTerminalsTest;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.powsybl.iidm.network.VariantManagerConstants.INITIAL_VARIANT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class ReferenceTerminalsTest extends AbstractReferenceTerminalsTest {

    @Test
    @Override
    public void testVariants() {
        // adapted from the TCK test: same scenario and assertions except the
        // Terminal.getReferrers() counts, which are not wired for this network level extension in
        // the network store implementation (the per variant reference terminals are stored in the
        // per variant network resource, and removed connectables are filtered out when reading)
        Network network = FourSubstationsNodeBreakerFactory.create();
        VariantManager variantManager = network.getVariantManager();
        Generator gh1 = network.getGenerator("GH1");
        Generator gh2 = network.getGenerator("GH2");
        Generator gh3 = network.getGenerator("GH3");

        network.newExtension(ReferenceTerminalsAdder.class)
                .withTerminals(Set.of(gh1.getTerminal()))
                .add();
        ReferenceTerminals ext = network.getExtension(ReferenceTerminals.class);

        // create variants
        String variant1 = "variant1";
        String variant2 = "variant2";
        List<String> targetVariantIds = Arrays.asList(variant1, variant2);
        variantManager.cloneVariant(INITIAL_VARIANT_ID, targetVariantIds);

        // add gh2 to variant1
        variantManager.setWorkingVariant(variant1);
        ext.addReferenceTerminal(gh2.getTerminal());
        // add gh3 to variant2
        variantManager.setWorkingVariant(variant2);
        ext.addReferenceTerminal(gh3.getTerminal());

        // initial variant unmodified
        variantManager.setWorkingVariant(INITIAL_VARIANT_ID);
        assertEquals(1, ext.getReferenceTerminals().size());
        assertTrue(ext.getReferenceTerminals().contains(gh1.getTerminal()));

        // check variant 1 as expected
        variantManager.setWorkingVariant(variant1);
        assertEquals(2, ext.getReferenceTerminals().size());
        assertTrue(ext.getReferenceTerminals().containsAll(Set.of(gh1.getTerminal(), gh2.getTerminal())));

        // check variant 2 as expected
        variantManager.setWorkingVariant(variant2);
        assertEquals(2, ext.getReferenceTerminals().size());
        assertTrue(ext.getReferenceTerminals().containsAll(Set.of(gh1.getTerminal(), gh3.getTerminal())));

        // clear variant 1
        variantManager.setWorkingVariant(variant1);
        ext.reset();

        // check variant 1 empty
        assertEquals(0, ext.getReferenceTerminals().size());

        // check other variants unchanged
        variantManager.setWorkingVariant(INITIAL_VARIANT_ID);
        assertTrue(ext.getReferenceTerminals().contains(gh1.getTerminal()));
        variantManager.setWorkingVariant(variant2);
        assertTrue(ext.getReferenceTerminals().containsAll(Set.of(gh1.getTerminal(), gh3.getTerminal())));

        // test variant recycling
        String variant3 = "variant3";
        variantManager.removeVariant(variant1);
        variantManager.cloneVariant(variant2, variant3);
        variantManager.setWorkingVariant(variant3);
        assertTrue(ext.getReferenceTerminals().containsAll(Set.of(gh1.getTerminal(), gh3.getTerminal())));

        variantManager.removeVariant(variant2);
    }

    @Override
    public void testWithSubnetwork() {
        // network merging and subnetworks are not supported by the network store implementation
    }

    @Override
    public void testListenersTransferOnMergeAndDetach() {
        // network merging and subnetworks are not supported by the network store implementation
    }
}
