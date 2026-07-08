/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logs, once per class, when an extension attributes copy takes the Jackson fallback instead of a
 * generated structural copier — so an unexpectedly slow variant clone can be traced to the class
 * missing from {@link AttributesCopier#EXTENSION_COPIERS}. {@link RawExtensionAttributes} is
 * excluded: it carries an arbitrary json tree of extensions unknown on this side and is meant to be
 * copied by Jackson, so logging it would be noise, not a signal.
 *
 * <p>The log is at DEBUG (the fallback is correct, only slower) and deduplicated per class, so it is
 * safe on the per-extension clone hot path: enable it to detect fallbacks, and the volume is bounded
 * by the number of distinct classes ever seen.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
final class ExtensionCopyFallbackLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttributesCopier.class);

    private static final Set<Class<?>> LOGGED_CLASSES = ConcurrentHashMap.newKeySet();

    private ExtensionCopyFallbackLogger() {
    }

    /**
     * Flags a variant-clone extension copy that fell back to Jackson because no structural copier is
     * registered for {@code extensionClass}. The expected fallback ({@link RawExtensionAttributes})
     * is ignored, and each class is logged at most once.
     *
     * @return {@code true} if this call flagged (and, at DEBUG, logged) an unexpected fallback, i.e.
     *     the first time an unknown non-{@link RawExtensionAttributes} class is seen
     */
    static boolean logIfUnexpected(Class<? extends ExtensionAttributes> extensionClass) {
        if (extensionClass == RawExtensionAttributes.class || !LOGGED_CLASSES.add(extensionClass)) {
            return false;
        }
        // slf4j gates the actual output on the configured level; the dedup above bounds the volume
        LOGGER.debug("No structural copier for extension attributes class {}, falling back to the Jackson copy "
                + "for the variant clone; register a copier in AttributesCopier.EXTENSION_COPIERS to avoid it",
            extensionClass.getName());
        return true;
    }
}
