/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl;

import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security-analysis-shaped workflow, the canonical downstream usage of variant multi-thread access: the main thread
 * clones one variant per contingency, worker threads each pin a variant, apply their contingency and modifications,
 * and the main thread then visits the variants to collect results and removes them.
 */
class VariantMultiThreadWorkflowTest {

    private static final int CONTINGENCY_COUNT = 4;
    private static final double INITIAL_TARGET_P = 607;

    private static String variantId(int i) {
        return "contingency-" + i;
    }

    private static String contingencyLineId(int i) {
        return i % 2 == 0 ? "NHV1_NHV2_1" : "NHV1_NHV2_2";
    }

    private static String otherLineId(int i) {
        return i % 2 == 0 ? "NHV1_NHV2_2" : "NHV1_NHV2_1";
    }

    @Test
    void securityAnalysisShapedWorkflow() throws Exception {
        Network network = EurostagTutorialExample1Factory.create();
        VariantManager variantManager = network.getVariantManager();

        // variant management stays single-threaded, per the multi-thread access contract
        for (int i = 0; i < CONTINGENCY_COUNT; i++) {
            variantManager.cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, variantId(i));
        }
        variantManager.allowVariantMultiThreadAccess(true);

        CyclicBarrier barrier = new CyclicBarrier(CONTINGENCY_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(CONTINGENCY_COUNT);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < CONTINGENCY_COUNT; i++) {
                int contingencyIndex = i;
                futures.add(executor.submit(() -> {
                    variantManager.setWorkingVariant(variantId(contingencyIndex));
                    barrier.await();

                    // apply the contingency: disconnect one of the two parallel lines
                    Line line = network.getLine(contingencyLineId(contingencyIndex));
                    line.getTerminal1().disconnect();
                    line.getTerminal2().disconnect();

                    // apply a redispatch
                    network.getGenerator("GEN").setTargetP(INITIAL_TARGET_P + contingencyIndex + 1);

                    // this variant sees its own modifications
                    assertFalse(network.getLine(contingencyLineId(contingencyIndex)).getTerminal1().isConnected());
                    assertTrue(network.getLine(otherLineId(contingencyIndex)).getTerminal1().isConnected());
                    assertEquals(INITIAL_TARGET_P + contingencyIndex + 1, network.getGenerator("GEN").getTargetP(), 0);
                    assertEquals(600, network.getLoad("LOAD").getP0(), 0);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // the initial variant is untouched
        assertEquals(VariantManagerConstants.INITIAL_VARIANT_ID, variantManager.getWorkingVariantId());
        assertTrue(network.getLine("NHV1_NHV2_1").getTerminal1().isConnected());
        assertTrue(network.getLine("NHV1_NHV2_2").getTerminal1().isConnected());
        assertEquals(INITIAL_TARGET_P, network.getGenerator("GEN").getTargetP(), 0);

        // the main thread visits each variant to collect results: it must see the modifications made by the worker
        // threads, served through the shared client cache
        for (int i = 0; i < CONTINGENCY_COUNT; i++) {
            variantManager.setWorkingVariant(variantId(i));
            assertFalse(network.getLine(contingencyLineId(i)).getTerminal1().isConnected());
            assertTrue(network.getLine(otherLineId(i)).getTerminal1().isConnected());
            assertEquals(INITIAL_TARGET_P + i + 1, network.getGenerator("GEN").getTargetP(), 0);
        }

        // cleanup, still from the main thread
        variantManager.setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
        for (int i = 0; i < CONTINGENCY_COUNT; i++) {
            variantManager.removeVariant(variantId(i));
        }
        assertEquals(Set.of(VariantManagerConstants.INITIAL_VARIANT_ID), Set.copyOf(variantManager.getVariantIds()));
        variantManager.allowVariantMultiThreadAccess(false);
        assertEquals(INITIAL_TARGET_P, network.getGenerator("GEN").getTargetP(), 0);
    }
}
