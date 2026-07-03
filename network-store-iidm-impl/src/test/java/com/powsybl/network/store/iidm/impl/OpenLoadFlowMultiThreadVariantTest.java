/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl;

import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysis;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.SecurityAnalysisRunParameters;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Run a real computation engine (open-loadflow) on a network store network, the way downstream gridsuite services
 * would: parallel load flows on distinct variants with variant multi-thread access enabled, and a multi-thread
 * security analysis.
 */
class OpenLoadFlowMultiThreadVariantTest {

    @Test
    void parallelLoadFlowsOnDistinctVariants() throws Exception {
        Network network = EurostagTutorialExample1Factory.create();
        VariantManager variantManager = network.getVariantManager();

        // two variants with different load setpoints, so the load flow results must differ
        variantManager.cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "lf-1");
        variantManager.cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "lf-2");
        variantManager.setWorkingVariant("lf-2");
        network.getLoad("LOAD").setP0(650);
        variantManager.setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);

        variantManager.allowVariantMultiThreadAccess(true);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Double>> loadP = executor.invokeAll(List.of(
                () -> runLoadFlowAndGetLoadP(network, variantManager, "lf-1"),
                () -> runLoadFlowAndGetLoadP(network, variantManager, "lf-2")));
            assertEquals(600, loadP.get(0).get(60, TimeUnit.SECONDS), 1);
            assertEquals(650, loadP.get(1).get(60, TimeUnit.SECONDS), 1);
        } finally {
            executor.shutdownNow();
        }

        // the initial variant was not computed and keeps its state
        assertEquals(VariantManagerConstants.INITIAL_VARIANT_ID, variantManager.getWorkingVariantId());
        assertEquals(600, network.getLoad("LOAD").getP0(), 0);
    }

    private static double runLoadFlowAndGetLoadP(Network network, VariantManager variantManager, String variantId) {
        variantManager.setWorkingVariant(variantId);
        LoadFlowParameters parameters = new LoadFlowParameters();
        // the network store implementation has no ExtensionAdderProvider for the ReferenceTerminals extension that
        // open-loadflow writes by default; this is a pre-existing gap unrelated to variant multi-thread access
        OpenLoadFlowParameters.create(parameters).setWriteReferenceTerminals(false);
        LoadFlowResult result = LoadFlow.run(network, parameters);
        assertTrue(result.isFullyConverged());
        return network.getLoad("LOAD").getTerminal().getP();
    }

    @Test
    void multiThreadSecurityAnalysis() {
        Network network = EurostagTutorialExample1Factory.create();

        List<Contingency> contingencies = List.of(
            Contingency.line("NHV1_NHV2_1"),
            Contingency.line("NHV1_NHV2_2"));

        SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();
        parameters.addExtension(OpenSecurityAnalysisParameters.class,
            new OpenSecurityAnalysisParameters().setThreadCount(2));

        // with threadCount > 1, open-loadflow calls allowVariantMultiThreadAccess(true) itself and each worker
        // thread selects the working variant and reads the shared IIDM network to build its own LfNetwork, so this
        // run requires variant multi-thread access support (it throws on the former stub)
        SecurityAnalysisResult result = SecurityAnalysis.run(network, contingencies,
            SecurityAnalysisRunParameters.getDefault().setSecurityAnalysisParameters(parameters)).getResult();
        assertNotNull(result);
        assertEquals(2, result.getPostContingencyResults().size());
        // open-loadflow restores the multi-thread access state after the analysis
        assertFalse(network.getVariantManager().isVariantMultiThreadAccessAllowed());
    }
}
