/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.reflect.ClassPath;
import com.powsybl.cgmes.model.CgmesSubset;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.DiscreteMeasurement;
import com.powsybl.iidm.network.extensions.LoadConnectionType;
import com.powsybl.iidm.network.extensions.Measurement;
import com.powsybl.iidm.network.extensions.WindingConnectionType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class AttributesCopierTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static Resource<GeneratorAttributes> createGeneratorResource() {
        // no extension attributes here: their jackson (de)serialization needs the extension loaders,
        // registered in the iidm-impl module; they are covered by StructuralCloneDifferentialTest
        List<ReactiveCapabilityCurvePointAttributes> points = new ArrayList<>();
        points.add(ReactiveCapabilityCurvePointAttributes.builder().p(10).minQ(-5).maxQ(5).build());
        points.add(ReactiveCapabilityCurvePointAttributes.builder().p(20).minQ(-10).maxQ(10).build());
        TreeMap<Double, ReactiveCapabilityCurvePointAttributes> curve = new TreeMap<>();
        points.forEach(point -> curve.put(point.getP(), point));
        Map<String, String> properties = new HashMap<>();
        properties.put("key", "value");
        return Resource.generatorBuilder()
            .id("gen1")
            .variantNum(0)
            .attributes(GeneratorAttributes.builder()
                .name("gen1")
                .voltageLevelId("vl1")
                .bus("bus1")
                .minP(0)
                .maxP(100)
                .targetP(50)
                .targetV(400)
                .properties(properties)
                .reactiveLimits(ReactiveCapabilityCurveAttributes.builder().points(curve).build())
                .build())
            .build();
    }

    @Test
    void deepCopyIsIndependentFromTheSource() {
        Resource<GeneratorAttributes> resource = createGeneratorResource();

        List<Resource<GeneratorAttributes>> cloned = Resource.cloneResourcesToVariant(List.of(resource), 3, objectMapper, null);
        assertEquals(1, cloned.size());
        Resource<GeneratorAttributes> clonedResource = cloned.get(0);

        // the clone carries the new variant number and its own back reference
        assertEquals(3, clonedResource.getVariantNum());
        assertEquals(0, resource.getVariantNum());
        assertSame(clonedResource, clonedResource.getAttributes().getResource());

        // same state...
        assertEquals("gen1", clonedResource.getAttributes().getName());
        assertEquals(50, clonedResource.getAttributes().getTargetP(), 0);
        assertEquals("value", clonedResource.getAttributes().getProperties().get("key"));
        ReactiveCapabilityCurveAttributes curve = (ReactiveCapabilityCurveAttributes) clonedResource.getAttributes().getReactiveLimits();
        assertEquals(2, curve.getPoints().size());

        // ...but fully independent: mutating the source does not touch the clone
        resource.getAttributes().setTargetP(99);
        resource.getAttributes().getProperties().put("key", "otherValue");
        ((ReactiveCapabilityCurveAttributes) resource.getAttributes().getReactiveLimits()).getPoints().clear();

        assertEquals(50, clonedResource.getAttributes().getTargetP(), 0);
        assertEquals("value", clonedResource.getAttributes().getProperties().get("key"));
        assertEquals(2, curve.getPoints().size());
        assertNotSame(resource.getAttributes(), clonedResource.getAttributes());
    }

    @Test
    void nullCollectionsStayNull() {
        // the previous jackson clone kept absent fields null: the structural copy must not replace
        // them with defaults
        Resource<LoadAttributes> resource = Resource.loadBuilder()
            .id("load1")
            .variantNum(0)
            .attributes(LoadAttributes.builder()
                .voltageLevelId("vl1")
                .p0(10)
                .build())
            .build();
        resource.getAttributes().setExtensionAttributes(null);
        resource.getAttributes().setProperties(null);

        Resource<LoadAttributes> clonedResource = Resource.cloneResourcesToVariant(List.of(resource), 1, objectMapper, null).get(0);
        assertNull(clonedResource.getAttributes().getExtensionAttributes());
        assertNull(clonedResource.getAttributes().getProperties());
    }

    // jackson serialization of extension attributes normally goes through the extension loaders
    // (registered in the iidm-impl module) to resolve the type id: remove the type info to compare
    // the json of extension attributes within this module
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    private interface WithoutTypeInfoMixin {
    }

    private static ObservabilityQualityAttributes quality(double standardDeviation, Boolean redundant) {
        return ObservabilityQualityAttributes.builder().standardDeviation(standardDeviation).redundant(redundant).build();
    }

    private static LegFortescueAttributes legFortescue(double rz) {
        return LegFortescueAttributes.builder().rz(rz).xz(2).freeFluxes(true)
            .connectionType(WindingConnectionType.Y_GROUNDED).groundingR(3).groundingX(4).build();
    }

    private static TerminalRefAttributes terminalRef(String connectableId) {
        return TerminalRefAttributes.builder().connectableId(connectableId).side("ONE").build();
    }

    /**
     * One fully populated instance of every extension attributes class defined in this module.
     */
    private static List<ExtensionAttributes> populatedModelExtensions() {
        List<ExtensionAttributes> extensions = new ArrayList<>();
        extensions.add(ActivePowerControlAttributes.builder()
            .participate(true).droop(4).participationFactor(1.5).minTargetP(10).maxTargetP(90).build());
        extensions.add(BranchObservabilityAttributes.builder()
            .observable(true).qualityP1(quality(0.1, true)).qualityP2(quality(0.2, false))
            .qualityQ1(quality(0.3, true)).qualityQ2(quality(0.4, null)).build());
        extensions.add(new CgmesMetadataModelsAttributes(new ArrayList<>(List.of(
            new CgmesMetadataModelAttributes(CgmesSubset.STATE_VARIABLES, "modelId", "description", 2, "modelingAuthority",
                new ArrayList<>(List.of("profile1", "profile2")), new ArrayList<>(List.of("dependency")), new ArrayList<>(List.of("superseded")))))));
        extensions.add(new CgmesTapChangersAttributes(new ArrayList<>(List.of(CgmesTapChangerAttributes.builder()
            .id("tc1").combinedTapChangerId("ctc1").type("someType").hidden(true).step(3).controlId("control1").build()))));
        extensions.add(DiscreteMeasurementsAttributes.builder()
            .discreteMeasurementAttributes(new ArrayList<>(List.of(DiscreteMeasurementAttributes.builder()
                .id("dm1").type(DiscreteMeasurement.Type.TAP_POSITION).tapChanger(DiscreteMeasurement.TapChanger.RATIO_TAP_CHANGER)
                .valueType(DiscreteMeasurement.ValueType.INT).properties(new HashMap<>(Map.of("key", "value")))
                .value(5).valid(true).build())))
            .build());
        extensions.add(DynamicModelInfoAttributes.builder().modelName("model").build());
        extensions.add(GeneratorFortescueAttributes.builder()
            .rz(1).xz(2).rn(3).xn(4).grounded(true).groundingR(5).groundingX(6).build());
        extensions.add(GeneratorStartupAttributes.builder()
            .plannedActivePowerSetpoint(100).startupCost(5).marginalCost(10).plannedOutageRate(0.1).forcedOutageRate(0.2).build());
        extensions.add(InjectionObservabilityAttributes.builder()
            .observable(true).qualityP(quality(0.1, true)).qualityQ(quality(0.2, false)).qualityV(quality(0.3, null)).build());
        extensions.add(legFortescue(1));
        extensions.add(LineFortescueAttributes.builder()
            .rz(1).xz(2).openPhaseA(true).openPhaseB(false).openPhaseC(true).build());
        extensions.add(LinePositionAttributes.builder()
            .coordinates(new ArrayList<>(List.of(new Coordinate(1, 2), new Coordinate(3, 4)))).build());
        extensions.add(LoadAsymmetricalAttributes.builder()
            .connectionType(LoadConnectionType.DELTA).deltaPa(1).deltaQa(2).deltaPb(3).deltaQb(4).deltaPc(5).deltaQc(6).build());
        extensions.add(MeasurementsAttributes.builder()
            .measurementAttributes(new ArrayList<>(List.of(MeasurementAttributes.builder()
                .id("m1").type(Measurement.Type.ACTIVE_POWER).properties(new HashMap<>(Map.of("key", "value")))
                .side(1).value(42).standardDeviation(0.5).valid(true).build())))
            .build());
        extensions.add(OperatingStatusAttributes.builder().operatingStatus("PLANNED_OUTAGE").build());
        extensions.add(new ReferencePrioritiesAttributes(new ArrayList<>(List.of(ReferencePriorityAttributes.builder()
            .terminal(terminalRef("gen1")).priority(1).build()))));
        extensions.add(SecondaryVoltageControlAttributes.builder()
            .controlZones(new ArrayList<>(List.of(ControlZoneAttributes.builder()
                .name("zone1")
                .pilotPoint(PilotPointAttributes.builder()
                    .busbarSectionsOrBusesIds(new ArrayList<>(List.of("bus1", "bus2"))).targetV(400).build())
                .controlUnits(new ArrayList<>(List.of(ControlUnitAttributes.builder().id("unit1").participate(true).build())))
                .build())))
            .build());
        extensions.add(SubstationPositionAttributes.builder().coordinate(new Coordinate(1, 2)).build());
        extensions.add(ThreeWindingsTransformerFortescueAttributes.builder()
            .leg1(legFortescue(1)).leg2(legFortescue(2)).leg3(legFortescue(3)).build());
        extensions.add(ThreeWindingsTransformerToBeEstimatedAttributes.builder()
            .rtc1Status(true).rtc2Status(false).rtc3Status(true).ptc1Status(false).ptc2Status(true).ptc3Status(false).build());
        extensions.add(TwoWindingsTransformerFortescueAttributes.builder()
            .rz(1).xz(2).freeFluxes(true).connectionType1(WindingConnectionType.Y).connectionType2(WindingConnectionType.DELTA)
            .groundingR1(3).groundingX1(4).groundingR2(5).groundingX2(6).build());
        extensions.add(TwoWindingsTransformerToBeEstimatedAttributes.builder().rtcStatus(true).ptcStatus(true).build());
        extensions.add(VoltageRegulationAttributes.builder()
            .voltageRegulatorOn(true).targetV(400).regulatingTerminal(terminalRef("gen2")).build());
        return extensions;
    }

    @Test
    void everyModelExtensionAttributesClassIsCopiedStructurally() throws JsonProcessingException {
        ObjectMapper withoutTypeInfo = new ObjectMapper().addMixIn(ExtensionAttributes.class, WithoutTypeInfoMixin.class);
        List<ExtensionAttributes> extensions = populatedModelExtensions();
        assertEquals(AttributesCopier.EXTENSION_COPIERS.keySet(),
            extensions.stream().map(ExtensionAttributes::getClass).collect(Collectors.toSet()),
            "populatedModelExtensions() must build one instance of every registered extension attributes class");

        for (ExtensionAttributes extension : extensions) {
            // going through the dispatch: a registered class must not touch the object mapper
            // (extension loaders are not registered in this module, jackson would fail)
            ExtensionAttributes copy = AttributesCopier.INSTANCE.copy(extension, objectMapper);
            assertNotSame(extension, copy);
            assertSame(extension.getClass(), copy.getClass());
            assertEquals(withoutTypeInfo.writeValueAsString(extension), withoutTypeInfo.writeValueAsString(copy),
                "structural copy of " + extension.getClass().getSimpleName() + " differs from its source");
        }
    }

    @Test
    void copiedExtensionNestedStateIsNotShared() {
        List<ExtensionAttributes> extensions = populatedModelExtensions();
        for (ExtensionAttributes extension : extensions) {
            ExtensionAttributes copy = AttributesCopier.INSTANCE.copy(extension, objectMapper);
            switch (copy) {
                case MeasurementsAttributes measurements -> {
                    measurements.getMeasurementAttributes().get(0).getProperties().put("other", "value");
                    measurements.getMeasurementAttributes().clear();
                    MeasurementsAttributes source = (MeasurementsAttributes) extension;
                    assertEquals(1, source.getMeasurementAttributes().size());
                    assertEquals(Map.of("key", "value"), source.getMeasurementAttributes().get(0).getProperties());
                }
                case DiscreteMeasurementsAttributes discreteMeasurements -> {
                    discreteMeasurements.getDiscreteMeasurementAttributes().get(0).getProperties().clear();
                    discreteMeasurements.getDiscreteMeasurementAttributes().clear();
                    DiscreteMeasurementsAttributes source = (DiscreteMeasurementsAttributes) extension;
                    assertEquals(1, source.getDiscreteMeasurementAttributes().size());
                    assertEquals(Map.of("key", "value"), source.getDiscreteMeasurementAttributes().get(0).getProperties());
                }
                case CgmesTapChangersAttributes tapChangers -> {
                    tapChangers.getCgmesTapChangers().get(0).setId("changed");
                    tapChangers.getCgmesTapChangers().clear();
                    CgmesTapChangersAttributes source = (CgmesTapChangersAttributes) extension;
                    assertEquals(1, source.getCgmesTapChangers().size());
                    assertEquals("tc1", source.getCgmesTapChangers().get(0).getId());
                }
                case CgmesMetadataModelsAttributes models -> {
                    models.getModels().get(0).getProfiles().clear();
                    models.getModels().clear();
                    CgmesMetadataModelsAttributes source = (CgmesMetadataModelsAttributes) extension;
                    assertEquals(1, source.getModels().size());
                    assertEquals(List.of("profile1", "profile2"), source.getModels().get(0).getProfiles());
                }
                case ReferencePrioritiesAttributes priorities -> {
                    priorities.getReferencePriorities().get(0).setPriority(99);
                    priorities.getReferencePriorities().clear();
                    ReferencePrioritiesAttributes source = (ReferencePrioritiesAttributes) extension;
                    assertEquals(1, source.getReferencePriorities().size());
                    assertEquals(1, source.getReferencePriorities().get(0).getPriority());
                }
                case SecondaryVoltageControlAttributes control -> {
                    control.getControlZones().get(0).getPilotPoint().getBusbarSectionsOrBusesIds().clear();
                    control.getControlZones().get(0).getControlUnits().clear();
                    control.getControlZones().clear();
                    SecondaryVoltageControlAttributes source = (SecondaryVoltageControlAttributes) extension;
                    assertEquals(1, source.getControlZones().size());
                    assertEquals(2, source.getControlZones().get(0).getPilotPoint().getBusbarSectionsOrBusesIds().size());
                    assertEquals(1, source.getControlZones().get(0).getControlUnits().size());
                }
                case LinePositionAttributes linePosition -> {
                    linePosition.getCoordinates().clear();
                    assertEquals(2, ((LinePositionAttributes) extension).getCoordinates().size());
                }
                case BranchObservabilityAttributes branchObservability -> {
                    branchObservability.getQualityP1().setStandardDeviation(99);
                    assertEquals(0.1, ((BranchObservabilityAttributes) extension).getQualityP1().getStandardDeviation(), 0);
                }
                default -> {
                    // scalar-only extension: nothing to share
                }
            }
        }
    }

    @Test
    void nullExtensionListsKeepTheJacksonCloneSemantics() {
        // the previous jackson copy went through the json where a null list is omitted, so the
        // clone kept the empty list of the no-args constructor: the structural copy must do the same
        MeasurementsAttributes measurements = new MeasurementsAttributes();
        measurements.setMeasurementAttributes(null);
        MeasurementsAttributes measurementsCopy = (MeasurementsAttributes) AttributesCopier.INSTANCE.copy((ExtensionAttributes) measurements, objectMapper);
        assertEquals(List.of(), measurementsCopy.getMeasurementAttributes());

        DiscreteMeasurementsAttributes discreteMeasurements = new DiscreteMeasurementsAttributes();
        discreteMeasurements.setDiscreteMeasurementAttributes(null);
        DiscreteMeasurementsAttributes discreteMeasurementsCopy =
            (DiscreteMeasurementsAttributes) AttributesCopier.INSTANCE.copy((ExtensionAttributes) discreteMeasurements, objectMapper);
        assertEquals(List.of(), discreteMeasurementsCopy.getDiscreteMeasurementAttributes());

        // no initializer here: a null list must stay null
        SecondaryVoltageControlAttributes control = new SecondaryVoltageControlAttributes();
        SecondaryVoltageControlAttributes controlCopy =
            (SecondaryVoltageControlAttributes) AttributesCopier.INSTANCE.copy((ExtensionAttributes) control, objectMapper);
        assertNull(controlCopy.getControlZones());
    }

    @Test
    void registryContainsExactlyTheModelExtensionAttributesClasses() throws IOException {
        // scan the module package: adding a new extension attributes class to the model without
        // registering a structural copier must fail this test
        Set<Class<?>> modelExtensionClasses = ClassPath.from(AttributesCopier.class.getClassLoader())
            .getTopLevelClasses(AttributesCopier.class.getPackageName()).stream()
            .map(ClassPath.ClassInfo::load)
            .filter(ExtensionAttributes.class::isAssignableFrom)
            .filter(clazz -> !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers()))
            .collect(Collectors.toSet());
        assertTrue(modelExtensionClasses.contains(RawExtensionAttributes.class));

        // RawExtensionAttributes is deliberately not copied structurally: it holds an arbitrary
        // json tree (unknown extensions), only the jackson fallback can copy it faithfully
        modelExtensionClasses.remove(RawExtensionAttributes.class);
        assertEquals(modelExtensionClasses, Set.copyOf(AttributesCopier.EXTENSION_COPIERS.keySet()));
    }
}
