/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl;

import com.powsybl.cgmes.conformity.CgmesConformity1Catalog;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.OperationalLimitsGroup;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * A new {@link OperationalLimitsGroupImpl} wrapper is created on each lookup, unlike in the core implementation
 * which always returns the same instance for a given group. Wrappers from different lookups must therefore be equal,
 * so that consumers keying maps with groups behave the same on both implementations.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class OperationalLimitsGroupEqualityTest {

    @Test
    void groupsFromDifferentLookupsAreInterchangeableAsMapKeys() {
        Network network = EurostagTutorialExample1Factory.create();
        Line line = network.getLine("NHV1_NHV2_1");
        line.newOperationalLimitsGroup1("g1");
        OperationalLimitsGroup first = line.getOperationalLimitsGroup1("g1").orElseThrow();
        OperationalLimitsGroup second = line.getOperationalLimitsGroup1("g1").orElseThrow();
        assertNotSame(first, second);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());

        // not equal to another group, to the same group id on the other side, or to the same group id on another line
        line.newOperationalLimitsGroup1("g2");
        line.newOperationalLimitsGroup2("g1");
        network.getLine("NHV1_NHV2_2").newOperationalLimitsGroup1("g1");
        assertNotEquals(first, line.getOperationalLimitsGroup1("g2").orElseThrow());
        assertNotEquals(first, line.getOperationalLimitsGroup2("g1").orElseThrow());
        assertNotEquals(first, network.getLine("NHV1_NHV2_2").getOperationalLimitsGroup1("g1").orElseThrow());
    }

    @Test
    void temporaryLimitsAreNotLostOnCgmesImport() {
        // the CGMES conversion accumulates the limits of a group in a single adder, cached in a map keyed by the
        // OperationalLimitsGroup: without wrapper equality, every CGMES limit row got its own adder and each add()
        // overwrote the limits written by the previous rows (269 of the 392 temporary limits of this grid were lost)
        Network network = Network.read(CgmesConformity1Catalog.smallBusBranch().dataSource());
        // 386 is the count obtained on the same grid with the in-memory reference implementation (lines and two
        // windings transformers)
        assertEquals(386, countTemporaryLimits(network));
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
}
