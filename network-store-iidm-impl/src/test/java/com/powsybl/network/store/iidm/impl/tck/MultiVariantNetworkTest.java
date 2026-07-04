/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl.tck;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.tck.AbstractMultiVariantNetworkTest;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class MultiVariantNetworkTest extends AbstractMultiVariantNetworkTest {

    private static final String SECOND_VARIANT = "SecondVariant";

    @Test
    @Override
    /* The TCK test obtains a Generator in the main thread and reads it from worker threads. In the network store
     * implementation, identifiable wrappers belong to the variant context of the thread that obtained them, so in
     * multi-thread access mode each thread must get its objects from the shared network; references must not be
     * passed between threads. This adapted version checks the same variant isolation under that contract. */
    public void multiThreadTest() throws InterruptedException {
        Network network = EurostagTutorialExample1Factory.create();
        VariantManager manager = network.getVariantManager();
        manager.allowVariantMultiThreadAccess(true);
        manager.cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, SECOND_VARIANT);
        manager.setWorkingVariant(SECOND_VARIANT);
        network.getGenerator("GEN").setTargetP(1000);
        manager.setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
        network.getGenerator("GEN").setTargetP(2000);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Double>> futures = executor.invokeAll(List.of(
                () -> {
                    manager.setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
                    Generator generator = network.getGenerator("GEN");
                    return generator.getTargetP();
                },
                () -> {
                    manager.setWorkingVariant(SECOND_VARIANT);
                    Generator generator = network.getGenerator("GEN");
                    return generator.getTargetP();
                }));
            try {
                assertEquals(2000, futures.get(0).get(30, TimeUnit.SECONDS), 0);
                assertEquals(1000, futures.get(1).get(30, TimeUnit.SECONDS), 0);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Override
    /* Not applicable to the network store implementation: in powsybl-core the network structure (equipment
     * existence) is shared by all variants and only state values are per variant, so an equipment created while on
     * one variant is visible from the others. Here each variant is a full copy on the server, structural changes are
     * per variant, and an equipment created on a variant does not exist on the other ones. */
    public void multiVariantTopologyTest() {
        // see comment above
    }
}
