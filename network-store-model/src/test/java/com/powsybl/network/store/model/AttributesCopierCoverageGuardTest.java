/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.model;

import com.google.common.reflect.ClassPath;
import org.junit.jupiter.api.Test;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Structural guard of {@link AttributesCopier}, in the spirit of open-loadflow's LfNetwork copy
 * reflection guard: it verifies that the generated copy cannot silently drop an attribute.
 *
 * <p>{@link AttributesCopier} is a MapStruct mapper with {@code unmappedTargetPolicy = ERROR}, so
 * the build already fails if a <b>writable</b> property (one with a setter) is not copied. The one
 * remaining hole is a <b>read-only</b> property (a getter with no setter): MapStruct maps by
 * accessor, so such a property is neither an unmapped target (there is no setter to fill) nor, by
 * default, a reported unmapped source — it is just silently left out of the copy. This is exactly
 * the shape of the hand-written copiers (getter-populated lists like the CGMES ones): they exist
 * <i>because</i> those properties are read-only.
 *
 * <p>This test scans every concrete {@link Attributes} class of the model and asserts that each of
 * its read-only properties is explicitly classified in {@link #ALLOWED_READ_ONLY} — as a derived
 * value that must not be copied, or as one a hand-written copier method takes care of. A new
 * read-only property (or a new attributes class carrying one) fails this test until its author
 * makes that decision, instead of quietly falling back to an incomplete copy.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class AttributesCopierCoverageGuardTest {

    /**
     * Read-only properties ({@code ClassSimpleName#propertyName}) that are intentionally not copied
     * field-for-field by MapStruct. Two families:
     * <ul>
     *   <li><b>derived</b> — computed from other (copied) properties, holds no own state, so the copy
     *       recomputes it (topology helper ids, resource type discriminators, the reactive limits
     *       kind constant...);</li>
     *   <li><b>custom-copied</b> — real state exposed only through a getter (no setter), handled
     *       explicitly by a hand-written copier method in {@link AttributesCopier} (the differential
     *       test pins that these are actually reproduced).</li>
     * </ul>
     * Anything not listed here is treated as a copy hole and fails the test.
     */
    private static final Set<String> ALLOWED_READ_ONLY = Set.of(
        // derived: the topology helper ids, computed from the (copied) voltageLevelId / substationId
        // and the branch/leg sides, are @JsonIgnore and hold no own state
        "BatteryAttributes#containerIds",
        "BoundaryLineAttributes#containerIds",
        "BoundaryLineAttributes#sideList",
        "BusbarSectionAttributes#containerIds",
        "ConfiguredBusAttributes#containerIds",
        "GeneratorAttributes#containerIds",
        "GroundAttributes#containerIds",
        "LccConverterStationAttributes#containerIds",
        "LineAttributes#containerIds",
        "LineAttributes#sideList",
        "LoadAttributes#containerIds",
        "ShuntCompensatorAttributes#containerIds",
        "StaticVarCompensatorAttributes#containerIds",
        "SwitchAttributes#containerIds",
        "ThreeWindingsTransformerAttributes#containerIds",
        "ThreeWindingsTransformerAttributes#sideList",
        "TwoWindingsTransformerAttributes#containerIds",
        "TwoWindingsTransformerAttributes#sideList",
        "VoltageLevelAttributes#containerIds",
        "VscConverterStationAttributes#containerIds",
        // derived: the selected operational limits group is looked up by the (copied)
        // selectedOperationalLimitsGroupId* in the (copied) operationalLimitsGroups map
        "BoundaryLineAttributes#selectedOperationalLimitsGroup",
        "LineAttributes#selectedOperationalLimitsGroup1",
        "LineAttributes#selectedOperationalLimitsGroup2",
        "TwoWindingsTransformerAttributes#selectedOperationalLimitsGroup1",
        "TwoWindingsTransformerAttributes#selectedOperationalLimitsGroup2",
        // derived: isFullVariant() is fullVariantNum == FULL_VARIANT_INDICATOR (fullVariantNum is copied)
        "NetworkAttributes#fullVariant"
    );

    @Test
    void everyReadOnlyPropertyOfEveryAttributesClassIsClassified() throws IOException {
        Set<String> unclassified = new TreeSet<>();
        for (Class<?> attributesClass : concreteAttributesClasses()) {
            for (String property : readOnlyProperties(attributesClass)) {
                String key = attributesClass.getSimpleName() + "#" + property;
                if (!ALLOWED_READ_ONLY.contains(key)) {
                    unclassified.add(key);
                }
            }
        }
        assertEquals(Set.of(), unclassified,
            "read-only attribute properties not classified in ALLOWED_READ_ONLY: MapStruct copies by "
                + "setter, so each of these is silently dropped by the clone unless a hand-written copier "
                + "handles it. Add a setter (let MapStruct copy it), or handle it in a custom copier / mark "
                + "it derived and list it in ALLOWED_READ_ONLY.");
    }

    private static List<Class<?>> concreteAttributesClasses() throws IOException {
        return ClassPath.from(AttributesCopier.class.getClassLoader())
            .getTopLevelClasses(AttributesCopier.class.getPackageName()).stream()
            .map(ClassPath.ClassInfo::load)
            .filter(Attributes.class::isAssignableFrom)
            .filter(clazz -> !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers()))
            .sorted(java.util.Comparator.comparing(Class::getSimpleName))
            .collect(Collectors.toList());
    }

    private static Set<String> readOnlyProperties(Class<?> clazz) {
        try {
            Set<String> readOnly = new TreeSet<>();
            for (PropertyDescriptor pd : Introspector.getBeanInfo(clazz, Object.class).getPropertyDescriptors()) {
                if (pd.getReadMethod() != null && pd.getWriteMethod() == null) {
                    readOnly.add(pd.getName());
                }
            }
            return readOnly;
        } catch (IntrospectionException e) {
            throw new IllegalStateException(e);
        }
    }
}
