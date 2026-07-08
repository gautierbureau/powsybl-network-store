/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class ExtensionCopyFallbackLoggerTest {

    /** A stand-in for a plugin extension class not registered in {@code EXTENSION_COPIERS}. */
    private static final class UnregisteredExtensionAttributes implements ExtensionAttributes {
    }

    @Test
    void anUnexpectedFallbackIsFlaggedOnceThenDeduplicated() {
        // first time this unknown class hits the Jackson fallback: flagged (and logged at DEBUG)
        assertTrue(ExtensionCopyFallbackLogger.logIfUnexpected(UnregisteredExtensionAttributes.class));
        // same class again: deduplicated, not flagged a second time
        assertFalse(ExtensionCopyFallbackLogger.logIfUnexpected(UnregisteredExtensionAttributes.class));
    }

    @Test
    void theExpectedRawExtensionFallbackIsNotFlagged() {
        // RawExtensionAttributes is meant to be copied by Jackson (arbitrary json tree of unknown
        // extensions), so it is never reported as a missing structural copier
        assertFalse(ExtensionCopyFallbackLogger.logIfUnexpected(RawExtensionAttributes.class));
        assertFalse(ExtensionCopyFallbackLogger.logIfUnexpected(RawExtensionAttributes.class));
    }
}
