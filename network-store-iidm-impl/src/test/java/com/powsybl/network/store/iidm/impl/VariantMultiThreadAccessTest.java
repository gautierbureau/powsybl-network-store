/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrent read access to distinct variants of the same network, each thread working on its own variant selected
 * with {@link VariantManager#setWorkingVariant(String)} after {@link VariantManager#allowVariantMultiThreadAccess(boolean)}
 * has been enabled.
 */
class VariantMultiThreadAccessTest {

    private static final double INITIAL_TARGET_P = 607;

    @Test
    void multiThreadReadAccessOnDistinctVariants() throws Exception {
        Network network = EurostagTutorialExample1Factory.create();
        VariantManager variantManager = network.getVariantManager();
        assertFalse(variantManager.isVariantMultiThreadAccessAllowed());

        // prepare two variants with a distinct generator target p on each
        variantManager.cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "v1");
        variantManager.cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "v2");
        variantManager.setWorkingVariant("v1");
        network.getGenerator("GEN").setTargetP(100);
        variantManager.setWorkingVariant("v2");
        network.getGenerator("GEN").setTargetP(200);
        variantManager.setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);

        variantManager.allowVariantMultiThreadAccess(true);
        assertTrue(variantManager.isVariantMultiThreadAccessAllowed());

        // the enabling thread keeps its working variant
        assertEquals(VariantManagerConstants.INITIAL_VARIANT_ID, variantManager.getWorkingVariantId());
        assertEquals(INITIAL_TARGET_P, network.getGenerator("GEN").getTargetP(), 0);

        int threadCount = 2;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                String variantId = "v" + (i + 1);
                double expectedTargetP = (i + 1) * 100;
                futures.add(executor.submit(() -> {
                    // a thread must select its working variant before any access
                    PowsyblException e = assertThrows(PowsyblException.class, network::getCaseDate);
                    assertEquals("Variant index not set", e.getMessage());

                    variantManager.setWorkingVariant(variantId);
                    barrier.await();
                    for (int j = 0; j < 100; j++) {
                        assertEquals(variantId, variantManager.getWorkingVariantId());
                        assertEquals(expectedTargetP, network.getGenerator("GEN").getTargetP(), 0);
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // the enabling thread is not affected by what other threads selected
        assertEquals(VariantManagerConstants.INITIAL_VARIANT_ID, variantManager.getWorkingVariantId());
        assertEquals(INITIAL_TARGET_P, network.getGenerator("GEN").getTargetP(), 0);

        variantManager.allowVariantMultiThreadAccess(false);
        assertFalse(variantManager.isVariantMultiThreadAccessAllowed());
        assertEquals(INITIAL_TARGET_P, network.getGenerator("GEN").getTargetP(), 0);
    }

    @Test
    void accessAfterVariantRemovedByAnotherThreadThrows() throws Exception {
        Network network = EurostagTutorialExample1Factory.create();
        VariantManager variantManager = network.getVariantManager();
        variantManager.cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "v1");
        variantManager.allowVariantMultiThreadAccess(true);

        // single thread executor so both submissions run on the same worker thread, sharing its variant context
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                variantManager.setWorkingVariant("v1");
                assertEquals(INITIAL_TARGET_P, network.getGenerator("GEN").getTargetP(), 0);
                return null;
            }).get(30, TimeUnit.SECONDS);

            variantManager.removeVariant("v1");

            executor.submit(() -> {
                PowsyblException e = assertThrows(PowsyblException.class, network::getCaseDate);
                assertEquals("Variant index not set", e.getMessage());
                // the thread can recover by selecting a living variant
                variantManager.setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
                assertEquals(INITIAL_TARGET_P, network.getGenerator("GEN").getTargetP(), 0);
                return null;
            }).get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void variantNumReuseByAnotherVariantInvalidatesThreadContext() throws Exception {
        Network network = EurostagTutorialExample1Factory.create();
        VariantManager variantManager = network.getVariantManager();
        variantManager.cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "v1");
        variantManager.allowVariantMultiThreadAccess(true);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                variantManager.setWorkingVariant("v1");
                assertEquals(INITIAL_TARGET_P, network.getGenerator("GEN").getTargetP(), 0);
                return null;
            }).get(30, TimeUnit.SECONDS);

            // remove v1 then create v2, which reuses the freed variant num: the worker thread context must not
            // silently read the new variant through its old context
            variantManager.removeVariant("v1");
            variantManager.cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "v2");

            executor.submit(() -> {
                PowsyblException e = assertThrows(PowsyblException.class, network::getCaseDate);
                assertEquals("Variant index not set", e.getMessage());
                return null;
            }).get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void variantSwitchInsideAThread() throws Exception {
        Network network = EurostagTutorialExample1Factory.create();
        VariantManager variantManager = network.getVariantManager();

        variantManager.cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "v1");
        variantManager.setWorkingVariant("v1");
        network.getGenerator("GEN").setTargetP(100);
        variantManager.setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);

        variantManager.allowVariantMultiThreadAccess(true);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                // a thread can switch variants like in single-thread mode, wrappers are reloaded in its own context
                variantManager.setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
                var gen = network.getGenerator("GEN");
                assertEquals(INITIAL_TARGET_P, gen.getTargetP(), 0);
                variantManager.setWorkingVariant("v1");
                assertEquals(100, gen.getTargetP(), 0);
                return null;
            }).get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // enabling thread still on its variant
        assertEquals(VariantManagerConstants.INITIAL_VARIANT_ID, variantManager.getWorkingVariantId());
        assertEquals(INITIAL_TARGET_P, network.getGenerator("GEN").getTargetP(), 0);
    }
}
