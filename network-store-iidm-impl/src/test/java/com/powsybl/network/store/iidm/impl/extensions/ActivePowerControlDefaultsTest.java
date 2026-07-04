/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl.extensions;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.ActivePowerControl;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class ActivePowerControlDefaultsTest {

    @Test
    void unsetValuesStayUnset() {
        Network network = EurostagTutorialExample1Factory.create();
        Generator generator = network.getGenerator("GEN");
        generator.newExtension(ActivePowerControlAdder.class)
                .withParticipate(true)
                .add();

        ActivePowerControl<Generator> activePowerControl = generator.getExtension(ActivePowerControl.class);
        assertTrue(activePowerControl.isParticipate());
        // like in the core implementation, an unset droop or participation factor stays NaN: a droop of 0 means
        // "no participation capacity" for load flow engines, while an absent one lets them fall back to their default
        assertTrue(Double.isNaN(activePowerControl.getDroop()));
        assertTrue(Double.isNaN(activePowerControl.getParticipationFactor()));
    }
}
