/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl;

import com.powsybl.cgmes.conformity.CgmesConformity1Catalog;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Importer;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.iidm.network.OperationalLimitsGroup;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysis;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.SecurityAnalysisRunParameters;
import com.powsybl.security.results.PostContingencyResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Differential test: the same multi-thread open-loadflow security analysis, on the same network, must give the same
 * results on the in-memory reference implementation (powsybl-iidm-impl) and on this implementation.
 */
class SecurityAnalysisImplComparisonTest {

    private static SecurityAnalysisResult runMultiThreadSecurityAnalysis(Network network, List<Contingency> contingencies, int threadCount) {
        SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();
        parameters.addExtension(OpenSecurityAnalysisParameters.class,
            new OpenSecurityAnalysisParameters().setThreadCount(threadCount));

        return SecurityAnalysis.run(network, contingencies,
            SecurityAnalysisRunParameters.getDefault().setSecurityAnalysisParameters(parameters)).getResult();
    }

    /**
     * Normalized text representation of a result: statuses and violations, sorted, numeric values rounded to 0.1 so
     * the comparison tolerates last-iteration numeric noise but nothing meaningful.
     */
    private static String describe(SecurityAnalysisResult result) {
        StringBuilder description = new StringBuilder();
        description.append("pre-contingency ").append(result.getPreContingencyResult().getStatus()).append('\n');
        result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().stream()
                .map(SecurityAnalysisImplComparisonTest::describe)
                .sorted()
                .forEach(violation -> description.append(violation).append('\n'));
        result.getPostContingencyResults().stream()
                .sorted(Comparator.comparing(r -> r.getContingency().getId()))
                .forEach(postContingencyResult -> {
                    description.append(postContingencyResult.getContingency().getId())
                            .append(' ').append(postContingencyResult.getStatus()).append('\n');
                    postContingencyResult.getLimitViolationsResult().getLimitViolations().stream()
                            .map(SecurityAnalysisImplComparisonTest::describe)
                            .sorted()
                            .forEach(violation -> description.append(violation).append('\n'));
                });
        return description.toString();
    }

    private static String describe(LimitViolation violation) {
        return String.format(Locale.US, "  %s %s %s limit=%.1f value=%.1f",
            violation.getSubjectId(), violation.getLimitType(), violation.getSide(),
            violation.getLimit(), violation.getValue());
    }

    private static long countTemporaryLimits(Network network) {
        List<OperationalLimitsGroup> groups = new ArrayList<>();
        network.getBranchStream().forEach(branch -> {
            groups.addAll(branch.getOperationalLimitsGroups1());
            groups.addAll(branch.getOperationalLimitsGroups2());
        });
        return groups.stream()
                .flatMap(group -> group.getCurrentLimits().stream())
                .mapToLong(limits -> limits.getTemporaryLimits().size())
                .sum();
    }

    private static boolean hasViolations(SecurityAnalysisResult result) {
        return result.getPostContingencyResults().stream()
                .map(PostContingencyResult::getLimitViolationsResult)
                .anyMatch(violations -> !violations.getLimitViolations().isEmpty());
    }

    private static void checkImplementations(Network inMemoryNetwork, Network storeNetwork) {
        // guard: make sure the two factories resolve to the two distinct implementations
        assertInstanceOf(NetworkImpl.class, storeNetwork);
        assertFalse(inMemoryNetwork instanceof NetworkImpl);
    }

    @Test
    void sameSecurityAnalysisResultsOnBothImplementations() {
        Network inMemoryNetwork = EurostagTutorialExample1Factory.createWithFixedCurrentLimits(NetworkFactory.find("Default"));
        Network storeNetwork = EurostagTutorialExample1Factory.createWithFixedCurrentLimits(NetworkFactory.find("NetworkStore"));
        checkImplementations(inMemoryNetwork, storeNetwork);

        List<Contingency> contingencies = List.of(
            Contingency.line("NHV1_NHV2_1"),
            Contingency.line("NHV1_NHV2_2"));

        SecurityAnalysisResult inMemoryResult = runMultiThreadSecurityAnalysis(inMemoryNetwork, contingencies, 2);
        SecurityAnalysisResult storeResult = runMultiThreadSecurityAnalysis(storeNetwork, contingencies, 2);

        // the comparison must be about something: the N-1 on one of the parallel lines overloads the other one
        assertTrue(hasViolations(inMemoryResult));

        assertEquals(describe(inMemoryResult), describe(storeResult));
    }

    /**
     * Same differential comparison on a bigger network: the CGMES conformity small grid (bus-branch), a real grid
     * model with native operational limits, imported once in each implementation, with an N-1 contingency on every
     * line and more security analysis threads.
     */
    @Test
    void sameSecurityAnalysisResultsOnBothImplementationsOnCgmesSmallGrid() {
        ReadOnlyDataSource dataSource = CgmesConformity1Catalog.smallBusBranch().dataSource();
        Importer importer = Importer.find(dataSource);
        Network inMemoryNetwork = importer.importData(dataSource, NetworkFactory.find("Default"), new Properties());
        Network storeNetwork = importer.importData(dataSource, NetworkFactory.find("NetworkStore"), new Properties());
        checkImplementations(inMemoryNetwork, storeNetwork);

        // guard against temporary limits being lost during import (the CGMES conversion accumulates the limits of a
        // group in a single adder cached with the OperationalLimitsGroup as key)
        long inMemoryTemporaryLimitCount = countTemporaryLimits(inMemoryNetwork);
        assertTrue(inMemoryTemporaryLimitCount > 300, "expected a real amount of temporary limits, got " + inMemoryTemporaryLimitCount);
        assertEquals(inMemoryTemporaryLimitCount, countTemporaryLimits(storeNetwork));

        // one N-1 contingency per line, built from the same ids on both networks
        List<Contingency> contingencies = inMemoryNetwork.getLineStream()
                .map(Identifiable::getId)
                .sorted()
                .map(Contingency::line)
                .toList();
        assertTrue(contingencies.size() > 50, "expected a real contingency list, got " + contingencies.size());
        assertEquals(contingencies.size(), storeNetwork.getLineCount());

        SecurityAnalysisResult inMemoryResult = runMultiThreadSecurityAnalysis(inMemoryNetwork, contingencies, 4);
        SecurityAnalysisResult storeResult = runMultiThreadSecurityAnalysis(storeNetwork, contingencies, 4);

        assertEquals(describe(inMemoryResult), describe(storeResult));
    }
}
