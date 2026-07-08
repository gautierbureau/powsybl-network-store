/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.powsybl.cgmes.conformity.CgmesConformity1Catalog;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.json.JsonUtil;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.DiscreteMeasurement;
import com.powsybl.iidm.network.extensions.LoadConnectionType;
import com.powsybl.iidm.network.extensions.Measurement;
import com.powsybl.iidm.network.extensions.WindingConnectionType;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.network.store.model.ActivePowerControlAttributes;
import com.powsybl.network.store.model.AttributesCopier;
import com.powsybl.network.store.model.BranchObservabilityAttributes;
import com.powsybl.network.store.model.CgmesMetadataModelAttributes;
import com.powsybl.network.store.model.CgmesMetadataModelsAttributes;
import com.powsybl.network.store.model.CgmesTapChangerAttributes;
import com.powsybl.network.store.model.CgmesTapChangersAttributes;
import com.powsybl.network.store.model.ControlUnitAttributes;
import com.powsybl.network.store.model.ControlZoneAttributes;
import com.powsybl.network.store.model.DiscreteMeasurementAttributes;
import com.powsybl.network.store.model.DiscreteMeasurementsAttributes;
import com.powsybl.network.store.model.DynamicModelInfoAttributes;
import com.powsybl.network.store.model.ExtensionAttributes;
import com.powsybl.network.store.model.ExtensionLoaders;
import com.powsybl.network.store.model.GeneratorFortescueAttributes;
import com.powsybl.network.store.model.GeneratorStartupAttributes;
import com.powsybl.network.store.model.IdentifiableAttributes;
import com.powsybl.network.store.model.InjectionObservabilityAttributes;
import com.powsybl.network.store.model.LineFortescueAttributes;
import com.powsybl.network.store.model.LinePositionAttributes;
import com.powsybl.network.store.model.LoadAsymmetricalAttributes;
import com.powsybl.network.store.model.LoadAttributes;
import com.powsybl.network.store.model.MeasurementAttributes;
import com.powsybl.network.store.model.MeasurementsAttributes;
import com.powsybl.network.store.model.ObservabilityQualityAttributes;
import com.powsybl.network.store.model.OperatingStatusAttributes;
import com.powsybl.network.store.model.PilotPointAttributes;
import com.powsybl.network.store.model.ReferencePrioritiesAttributes;
import com.powsybl.network.store.model.ReferencePriorityAttributes;
import com.powsybl.network.store.model.Resource;
import com.powsybl.network.store.model.SecondaryVoltageControlAttributes;
import com.powsybl.network.store.model.SubstationPositionAttributes;
import com.powsybl.network.store.model.TerminalRefAttributes;
import com.powsybl.network.store.model.ThreeWindingsTransformerToBeEstimatedAttributes;
import com.powsybl.network.store.model.TwoWindingsTransformerFortescueAttributes;
import com.powsybl.network.store.model.TwoWindingsTransformerToBeEstimatedAttributes;
import com.powsybl.network.store.model.VoltageRegulationAttributes;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Differential test of the structural resource copy: for every resource of real networks, cloning
 * through the structural copier must give exactly the same persisted state as the previous Jackson
 * round-trip clone. The comparison is on the canonical json of the cloned resources (the json view
 * is the persisted state; the resource back reference is json ignored on purpose).
 */
class StructuralCloneDifferentialTest {

    // same mapper construction as the clone call sites (CachedNetworkStoreClient, BufferedNetworkStoreClient)
    private static final ObjectMapper MAPPER = JsonUtil.createObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
        .configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);

    /**
     * The clone implementation this differential test guards against: a Jackson round trip.
     */
    private static Resource<IdentifiableAttributes> jacksonClone(Resource<IdentifiableAttributes> resource, int newVariantNum) {
        try (TokenBuffer buffer = new TokenBuffer(MAPPER, false)) {
            MAPPER.writeValue(buffer, resource);
            Resource<IdentifiableAttributes> clonedResource = MAPPER.readValue(buffer.asParser(), new TypeReference<>() {
            });
            clonedResource.setVariantNum(newVariantNum);
            return clonedResource;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkAllResources(Network network) throws IOException {
        NetworkImpl networkImpl = (NetworkImpl) network;
        List<Resource<IdentifiableAttributes>> resources = new ArrayList<>();
        resources.add((Resource<IdentifiableAttributes>) (Resource<?>) networkImpl.getResource());
        for (Identifiable<?> identifiable : network.getIdentifiables()) {
            if (identifiable instanceof AbstractIdentifiableImpl<?, ?> identifiableImpl) {
                resources.add((Resource<IdentifiableAttributes>) identifiableImpl.getResource());
            }
        }
        assertTrue(resources.size() > 10);

        for (Resource<IdentifiableAttributes> resource : resources) {
            Resource<IdentifiableAttributes> expected = jacksonClone(resource, 12);
            List<Resource<IdentifiableAttributes>> cloned = Resource.cloneResourcesToVariant(List.of(resource), 12, MAPPER, null);
            assertEquals(1, cloned.size());
            Resource<IdentifiableAttributes> actual = cloned.get(0);
            assertEquals(MAPPER.writeValueAsString(expected), MAPPER.writeValueAsString(actual),
                "structural clone differs from jackson clone for " + resource.getType() + " " + resource.getId());
            assertSame(actual, actual.getAttributes().getResource());
            assertEquals(12, actual.getVariantNum());
        }
    }

    @Test
    void sameCloneAsJacksonOnCgmesSmallGrid() throws IOException {
        checkAllResources(Network.read(CgmesConformity1Catalog.smallBusBranch().dataSource()));
    }

    @Test
    void sameCloneAsJacksonOnCgmesMicroGrid() throws IOException {
        // node/breaker network with switches, 3 windings transformers, tie lines, tap changers and cgmes extensions
        checkAllResources(Network.read(CgmesConformity1Catalog.microGridBaseCaseBE().dataSource()));
    }

    @Test
    void sameCloneAsJacksonOnEurostagWithExtensions() throws IOException {
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();
        Generator generator = network.getGenerator("GEN");
        generator.newExtension(ActivePowerControlAdder.class)
            .withParticipate(true)
            .withDroop(4)
            .withParticipationFactor(1.5)
            .add();
        generator.setProperty("testProperty", "testValue");
        checkAllResources(network);
    }

    private static ObservabilityQualityAttributes quality(double standardDeviation, Boolean redundant) {
        return ObservabilityQualityAttributes.builder().standardDeviation(standardDeviation).redundant(redundant).build();
    }

    private static TerminalRefAttributes terminalRef(String connectableId) {
        return TerminalRefAttributes.builder().connectableId(connectableId).side("ONE").build();
    }

    /**
     * A populated instance of every model extension attributes class registered in the loaders
     * of this module ({@code LegFortescueAttributes} has no loader of its own, it is covered as
     * a nested field of the three windings transformer fortescue extension).
     */
    private static List<ExtensionAttributes> populatedModelExtensions() {
        List<ExtensionAttributes> extensions = new ArrayList<>();
        extensions.add(ActivePowerControlAttributes.builder()
            .participate(true).droop(4).participationFactor(1.5).minTargetP(10).maxTargetP(90).build());
        extensions.add(BranchObservabilityAttributes.builder()
            .observable(true).qualityP1(quality(0.1, true)).qualityP2(quality(0.2, false))
            .qualityQ1(quality(0.3, true)).qualityQ2(quality(0.4, null)).build());
        // only the list fields: the jackson round trip cannot restore the scalar fields of
        // CgmesMetadataModelAttributes (getters only), the structural copy preserves them
        extensions.add(new CgmesMetadataModelsAttributes(new ArrayList<>(List.of(
            new CgmesMetadataModelAttributes(null, null, null, 0, null,
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
        // ThreeWindingsTransformerFortescueAttributes cannot be compared to a jackson round trip:
        // its LegFortescueAttributes fields implement ExtensionAttributes, so jackson tries to
        // resolve a type id for them and fails (no leg fortescue loader); the structural copy
        // handles it (covered by AttributesCopierTest in the model module)
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

    @SuppressWarnings("unchecked")
    @Test
    void sameCloneAsJacksonForEveryModelExtensionAttributesClass() throws IOException {
        Resource<LoadAttributes> loadResource = Resource.loadBuilder()
            .id("load1")
            .variantNum(0)
            .attributes(LoadAttributes.builder()
                .voltageLevelId("vl1")
                .p0(10)
                .build())
            .build();
        List<ExtensionAttributes> extensions = populatedModelExtensions();
        for (ExtensionAttributes extension : extensions) {
            loadResource.getAttributes().getExtensionAttributes()
                .put(ExtensionLoaders.findLoaderByAttributes(extension.getClass()).getName(), extension);
        }

        Resource<IdentifiableAttributes> resource = (Resource<IdentifiableAttributes>) (Resource<?>) loadResource;
        Resource<IdentifiableAttributes> expected = jacksonClone(resource, 12);
        Resource<IdentifiableAttributes> actual = Resource.cloneResourcesToVariant(List.of(resource), 12, MAPPER, null).get(0);
        assertEquals(MAPPER.writeValueAsString(expected), MAPPER.writeValueAsString(actual));

        // structural copies, not shared instances
        for (Map.Entry<String, ExtensionAttributes> entry : resource.getAttributes().getExtensionAttributes().entrySet()) {
            ExtensionAttributes copy = actual.getAttributes().getExtensionAttributes().get(entry.getKey());
            assertNotSame(entry.getValue(), copy, entry.getKey());
            assertSame(entry.getValue().getClass(), copy.getClass(), entry.getKey());
        }
    }

    @Test
    void pluginExtensionAttributesFallBackToTheJacksonCopy() {
        PluginTestExtensionAttributes extension = new PluginTestExtensionAttributes();
        extension.setPluginField("value");
        extension.setValues(new ArrayList<>(List.of("a", "b")));

        ExtensionAttributes copy = AttributesCopier.INSTANCE.copy((ExtensionAttributes) extension, MAPPER);

        assertNotSame(extension, copy);
        PluginTestExtensionAttributes pluginCopy = assertInstanceOf(PluginTestExtensionAttributes.class, copy);
        assertEquals(extension, pluginCopy);
        pluginCopy.getValues().add("c");
        assertEquals(List.of("a", "b"), extension.getValues());
    }

    @Test
    void subclassOfModelExtensionClassIsNotSlicedToItsBaseClassCopy() {
        // the structural copier dispatches on the exact runtime class: a (plugin) subclass of a
        // model class must not be structurally copied as its base class (that would silently drop
        // the subclass fields), it goes to the jackson fallback which needs a loader for the
        // concrete class, like before the structural copy (jackson wraps the loader lookup
        // failure in an IOException, rethrown unchecked)
        ActivePowerControlAttributes subclass = new ActivePowerControlAttributes() {
        };
        UncheckedIOException e = assertThrows(UncheckedIOException.class,
            () -> AttributesCopier.INSTANCE.copy((ExtensionAttributes) subclass, MAPPER));
        assertInstanceOf(PowsyblException.class, e.getCause().getCause());
    }
}
