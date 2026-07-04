/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.extensions.Coordinate;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.control.DeepClone;
import org.mapstruct.factory.Mappers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Structural (generated) deep copy of the resource attributes, used to clone resources to a new
 * variant without a Jackson serialization round trip. The copy methods are generated at compile
 * time by MapStruct from the getters and setters, so a new field is picked up automatically at
 * the next build.
 *
 * <p>Extension attributes classes form an open set at runtime (plugins can register new ones
 * through {@link ExtensionLoaders}), so they cannot all be enumerated at compile time: the
 * extension attributes classes defined in this module are copied structurally through the
 * {@link #EXTENSION_COPIERS} registry, looked up by exact concrete class; any other class (a
 * third-party plugin extension, {@link RawExtensionAttributes}, or a subclass of a model class)
 * falls back to a Jackson {@link TokenBuffer} round trip, exactly the copy the previous
 * implementation applied to the whole resource.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
// builders are disabled so the generated code uses the no-args constructor and the setters, exactly
// like the jackson deserialization the copy replaces: @Builder.Default values do not apply to fields
// that are null in the source attributes
@Mapper(mappingControl = DeepClone.class, unmappedTargetPolicy = ReportingPolicy.ERROR,
    builder = @org.mapstruct.Builder(disableBuilder = true))
public interface AttributesCopier {

    AttributesCopier INSTANCE = Mappers.getMapper(AttributesCopier.class);

    /**
     * Structural copiers for the extension attributes classes defined in this module, keyed by
     * exact concrete class. The lookup is deliberately by exact runtime class and not by
     * assignability: a plugin subclass of a model class must fall back to the Jackson copy, a
     * structural copy of the base class would silently drop the subclass fields.
     */
    Map<Class<? extends ExtensionAttributes>, BiFunction<ExtensionAttributes, ObjectMapper, ExtensionAttributes>> EXTENSION_COPIERS = Map.ofEntries(
        extensionCopier(ActivePowerControlAttributes.class, INSTANCE::copy),
        extensionCopier(BranchObservabilityAttributes.class, INSTANCE::copy),
        extensionCopier(CgmesMetadataModelsAttributes.class, INSTANCE::copy),
        extensionCopier(CgmesTapChangersAttributes.class, INSTANCE::copy),
        extensionCopier(DiscreteMeasurementsAttributes.class, INSTANCE::copy),
        extensionCopier(DynamicModelInfoAttributes.class, INSTANCE::copy),
        extensionCopier(GeneratorFortescueAttributes.class, INSTANCE::copy),
        extensionCopier(GeneratorStartupAttributes.class, INSTANCE::copy),
        extensionCopier(InjectionObservabilityAttributes.class, INSTANCE::copy),
        extensionCopier(LegFortescueAttributes.class, INSTANCE::copy),
        extensionCopier(LineFortescueAttributes.class, INSTANCE::copy),
        extensionCopier(LinePositionAttributes.class, INSTANCE::copy),
        extensionCopier(LoadAsymmetricalAttributes.class, INSTANCE::copy),
        extensionCopier(MeasurementsAttributes.class, INSTANCE::copy),
        extensionCopier(OperatingStatusAttributes.class, INSTANCE::copy),
        extensionCopier(ReferencePrioritiesAttributes.class, INSTANCE::copy),
        extensionCopier(SecondaryVoltageControlAttributes.class, INSTANCE::copy),
        extensionCopier(SubstationPositionAttributes.class, INSTANCE::copy),
        extensionCopier(ThreeWindingsTransformerFortescueAttributes.class, INSTANCE::copy),
        extensionCopier(ThreeWindingsTransformerToBeEstimatedAttributes.class, INSTANCE::copy),
        extensionCopier(TwoWindingsTransformerFortescueAttributes.class, INSTANCE::copy),
        extensionCopier(TwoWindingsTransformerToBeEstimatedAttributes.class, INSTANCE::copy),
        extensionCopier(VoltageRegulationAttributes.class, INSTANCE::copy)
    );

    private static <T extends ExtensionAttributes> Map.Entry<Class<? extends ExtensionAttributes>, BiFunction<ExtensionAttributes, ObjectMapper, ExtensionAttributes>> extensionCopier(
            Class<T> type, BiFunction<T, ObjectMapper, T> copier) {
        return Map.entry(type, (attributes, objectMapper) -> copier.apply(type.cast(attributes), objectMapper));
    }

    /**
     * Deep copies the attributes of a resource; the returned attributes have no
     * {@link Attributes#getResource() resource} back reference, the caller attaches them to their
     * new resource.
     */
    static IdentifiableAttributes copy(Resource<? extends IdentifiableAttributes> resource, ObjectMapper objectMapper) {
        IdentifiableAttributes attributes = resource.getAttributes();
        return switch (resource.getType()) {
            case NETWORK -> INSTANCE.copy((NetworkAttributes) attributes, objectMapper);
            case SUBSTATION -> INSTANCE.copy((SubstationAttributes) attributes, objectMapper);
            case VOLTAGE_LEVEL -> INSTANCE.copy((VoltageLevelAttributes) attributes, objectMapper);
            case LOAD -> INSTANCE.copy((LoadAttributes) attributes, objectMapper);
            case GENERATOR -> INSTANCE.copy((GeneratorAttributes) attributes, objectMapper);
            case BATTERY -> INSTANCE.copy((BatteryAttributes) attributes, objectMapper);
            case SHUNT_COMPENSATOR -> INSTANCE.copy((ShuntCompensatorAttributes) attributes, objectMapper);
            case VSC_CONVERTER_STATION -> INSTANCE.copy((VscConverterStationAttributes) attributes, objectMapper);
            case LCC_CONVERTER_STATION -> INSTANCE.copy((LccConverterStationAttributes) attributes, objectMapper);
            case STATIC_VAR_COMPENSATOR -> INSTANCE.copy((StaticVarCompensatorAttributes) attributes, objectMapper);
            case SWITCH -> INSTANCE.copy((SwitchAttributes) attributes, objectMapper);
            case BUSBAR_SECTION -> INSTANCE.copy((BusbarSectionAttributes) attributes, objectMapper);
            case TWO_WINDINGS_TRANSFORMER -> INSTANCE.copy((TwoWindingsTransformerAttributes) attributes, objectMapper);
            case THREE_WINDINGS_TRANSFORMER -> INSTANCE.copy((ThreeWindingsTransformerAttributes) attributes, objectMapper);
            case LINE -> INSTANCE.copy((LineAttributes) attributes, objectMapper);
            case TIE_LINE -> INSTANCE.copy((TieLineAttributes) attributes, objectMapper);
            case HVDC_LINE -> INSTANCE.copy((HvdcLineAttributes) attributes, objectMapper);
            case BOUNDARY_LINE -> INSTANCE.copy((BoundaryLineAttributes) attributes, objectMapper);
            case GROUND -> INSTANCE.copy((GroundAttributes) attributes, objectMapper);
            case CONFIGURED_BUS -> INSTANCE.copy((ConfiguredBusAttributes) attributes, objectMapper);
            case AREA -> INSTANCE.copy((AreaAttributes) attributes, objectMapper);
        };
    }

    // busCache is a runtime cache of iidm bus objects (json ignored), not part of the persisted state
    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "busCache", ignore = true)
    NetworkAttributes copy(NetworkAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    SubstationAttributes copy(SubstationAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "containerIds", ignore = true)
    VoltageLevelAttributes copy(VoltageLevelAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "containerIds", ignore = true)
    LoadAttributes copy(LoadAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "containerIds", ignore = true)
    GeneratorAttributes copy(GeneratorAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "containerIds", ignore = true)
    BatteryAttributes copy(BatteryAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "containerIds", ignore = true)
    ShuntCompensatorAttributes copy(ShuntCompensatorAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "containerIds", ignore = true)
    VscConverterStationAttributes copy(VscConverterStationAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "containerIds", ignore = true)
    LccConverterStationAttributes copy(LccConverterStationAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "containerIds", ignore = true)
    StaticVarCompensatorAttributes copy(StaticVarCompensatorAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "containerIds", ignore = true)
    SwitchAttributes copy(SwitchAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "containerIds", ignore = true)
    BusbarSectionAttributes copy(BusbarSectionAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "containerIds", ignore = true)
    @Mapping(target = "sideList", ignore = true)
    TwoWindingsTransformerAttributes copy(TwoWindingsTransformerAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "containerIds", ignore = true)
    @Mapping(target = "sideList", ignore = true)
    ThreeWindingsTransformerAttributes copy(ThreeWindingsTransformerAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "containerIds", ignore = true)
    @Mapping(target = "sideList", ignore = true)
    LineAttributes copy(LineAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    TieLineAttributes copy(TieLineAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    HvdcLineAttributes copy(HvdcLineAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "containerIds", ignore = true)
    @Mapping(target = "sideList", ignore = true)
    BoundaryLineAttributes copy(BoundaryLineAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "containerIds", ignore = true)
    GroundAttributes copy(GroundAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    @Mapping(target = "containerIds", ignore = true)
    ConfiguredBusAttributes copy(ConfiguredBusAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    AreaAttributes copy(AreaAttributes attributes, @Context ObjectMapper objectMapper);

    // index, side and type are json ignored runtime markers: the previous jackson copy dropped them,
    // the structural copy preserves that behavior
    @Mapping(target = "index", ignore = true)
    @Mapping(target = "side", ignore = true)
    @Mapping(target = "type", ignore = true)
    TapChangerStepAttributes copy(TapChangerStepAttributes attributes, @Context ObjectMapper objectMapper);

    // nested attributes carrying the (json ignored) resource back reference: never copied
    @Mapping(target = "resource", ignore = true)
    RatioTapChangerAttributes copy(RatioTapChangerAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    PhaseTapChangerAttributes copy(PhaseTapChangerAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    RegulatingPointAttributes copy(RegulatingPointAttributes attributes, @Context ObjectMapper objectMapper);

    @Mapping(target = "resource", ignore = true)
    AreaBoundaryAttributes copy(AreaBoundaryAttributes attributes, @Context ObjectMapper objectMapper);

    // the two closed polymorphic points of the attributes graph: dispatch by hand on the concrete type

    default ReactiveLimitsAttributes copy(ReactiveLimitsAttributes attributes, @Context ObjectMapper objectMapper) {
        if (attributes == null) {
            return null;
        }
        if (attributes instanceof MinMaxReactiveLimitsAttributes minMax) {
            return copy(minMax, objectMapper);
        }
        if (attributes instanceof ReactiveCapabilityCurveAttributes curve) {
            return copy(curve, objectMapper);
        }
        throw new PowsyblException("Unknown reactive limits attributes type: " + attributes.getClass().getName());
    }

    MinMaxReactiveLimitsAttributes copy(MinMaxReactiveLimitsAttributes attributes, @Context ObjectMapper objectMapper);

    ReactiveCapabilityCurveAttributes copy(ReactiveCapabilityCurveAttributes attributes, @Context ObjectMapper objectMapper);

    default ShuntCompensatorModelAttributes copy(ShuntCompensatorModelAttributes attributes, @Context ObjectMapper objectMapper) {
        if (attributes == null) {
            return null;
        }
        if (attributes instanceof ShuntCompensatorLinearModelAttributes linear) {
            return copy(linear, objectMapper);
        }
        if (attributes instanceof ShuntCompensatorNonLinearModelAttributes nonLinear) {
            return copy(nonLinear, objectMapper);
        }
        throw new PowsyblException("Unknown shunt compensator model attributes type: " + attributes.getClass().getName());
    }

    // hand written: the qPercent getter does not follow the javabeans property naming convention
    // mapstruct relies on (getQPercent resolves to property "QPercent")
    default CoordinatedReactiveControlAttributes copy(CoordinatedReactiveControlAttributes attributes, @Context ObjectMapper objectMapper) {
        if (attributes == null) {
            return null;
        }
        return new CoordinatedReactiveControlAttributes(attributes.getQPercent());
    }

    // hand written: the bPerSection/gPerSection getters do not follow the javabeans property naming
    // convention mapstruct relies on (getBPerSection resolves to property "BPerSection")
    default ShuntCompensatorLinearModelAttributes copy(ShuntCompensatorLinearModelAttributes attributes, @Context ObjectMapper objectMapper) {
        if (attributes == null) {
            return null;
        }
        return ShuntCompensatorLinearModelAttributes.builder()
            .bPerSection(attributes.getBPerSection())
            .gPerSection(attributes.getGPerSection())
            .maximumSectionCount(attributes.getMaximumSectionCount())
            .properties(attributes.getProperties() == null ? null : new java.util.HashMap<>(attributes.getProperties()))
            .build();
    }

    ShuntCompensatorNonLinearModelAttributes copy(ShuntCompensatorNonLinearModelAttributes attributes, @Context ObjectMapper objectMapper);

    /**
     * Extension attributes dispatch: the classes defined in this module are copied structurally
     * through the {@link #EXTENSION_COPIERS} registry (exact class lookup); any other class
     * (a plugin extension registered through {@link ExtensionLoaders}, {@link RawExtensionAttributes},
     * or a subclass of a model class) falls back to the Jackson copy, going through a token
     * buffer to skip the text encoding and parsing.
     */
    default ExtensionAttributes copy(ExtensionAttributes attributes, @Context ObjectMapper objectMapper) {
        if (attributes == null) {
            return null;
        }
        BiFunction<ExtensionAttributes, ObjectMapper, ExtensionAttributes> copier = EXTENSION_COPIERS.get(attributes.getClass());
        if (copier != null) {
            return copier.apply(attributes, objectMapper);
        }
        try (TokenBuffer buffer = new TokenBuffer(objectMapper, false)) {
            objectMapper.writeValue(buffer, attributes);
            return objectMapper.readValue(buffer.asParser(), ExtensionAttributes.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // the extension attributes classes defined in this module; their copy methods must not route
    // back through the generic ExtensionAttributes dispatch for their own type (each field is
    // declared with its concrete type, so mapstruct binds the specific methods below)

    ActivePowerControlAttributes copy(ActivePowerControlAttributes attributes, @Context ObjectMapper objectMapper);

    BranchObservabilityAttributes copy(BranchObservabilityAttributes attributes, @Context ObjectMapper objectMapper);

    // hand written: no setters (jackson populates the models list through the getter), and the
    // nested CgmesMetadataModelAttributes has no setters either
    default CgmesMetadataModelsAttributes copy(CgmesMetadataModelsAttributes attributes, @Context ObjectMapper objectMapper) {
        if (attributes == null) {
            return null;
        }
        CgmesMetadataModelsAttributes copy = new CgmesMetadataModelsAttributes();
        if (attributes.getModels() != null) {
            for (CgmesMetadataModelAttributes model : attributes.getModels()) {
                copy.getModels().add(copy(model, objectMapper));
            }
        }
        return copy;
    }

    // hand written: no setters, all state passed to the constructor
    default CgmesMetadataModelAttributes copy(CgmesMetadataModelAttributes attributes, @Context ObjectMapper objectMapper) {
        if (attributes == null) {
            return null;
        }
        return new CgmesMetadataModelAttributes(attributes.getSubset(), attributes.getId(), attributes.getDescription(),
            attributes.getVersion(), attributes.getModelingAuthoritySet(), copyStringList(attributes.getProfiles()),
            copyStringList(attributes.getDependentOn()), copyStringList(attributes.getSupersedes()));
    }

    private static List<String> copyStringList(List<String> list) {
        return list == null ? null : new ArrayList<>(list);
    }

    // hand written: no setters (jackson populates the cgmesTapChangers list through the getter)
    default CgmesTapChangersAttributes copy(CgmesTapChangersAttributes attributes, @Context ObjectMapper objectMapper) {
        if (attributes == null) {
            return null;
        }
        CgmesTapChangersAttributes copy = new CgmesTapChangersAttributes();
        if (attributes.getCgmesTapChangers() != null) {
            for (CgmesTapChangerAttributes tapChanger : attributes.getCgmesTapChangers()) {
                copy.getCgmesTapChangers().add(copy(tapChanger, objectMapper));
            }
        }
        return copy;
    }

    CgmesTapChangerAttributes copy(CgmesTapChangerAttributes attributes, @Context ObjectMapper objectMapper);

    // hand written: a null list source must keep the field initializer of the no-args constructor
    // (an empty list), like the jackson copy: the field is omitted from the json when null, so the
    // setter is never called on deserialization
    default DiscreteMeasurementsAttributes copy(DiscreteMeasurementsAttributes attributes, @Context ObjectMapper objectMapper) {
        if (attributes == null) {
            return null;
        }
        DiscreteMeasurementsAttributes copy = new DiscreteMeasurementsAttributes();
        if (attributes.getDiscreteMeasurementAttributes() != null) {
            List<DiscreteMeasurementAttributes> discreteMeasurements = new ArrayList<>(attributes.getDiscreteMeasurementAttributes().size());
            for (DiscreteMeasurementAttributes discreteMeasurement : attributes.getDiscreteMeasurementAttributes()) {
                discreteMeasurements.add(copy(discreteMeasurement, objectMapper));
            }
            copy.setDiscreteMeasurementAttributes(discreteMeasurements);
        }
        return copy;
    }

    // hand written: the value field is declared Object (a json scalar: string, boolean or
    // integer), which mapstruct cannot deep clone; scalars are immutable so sharing the
    // reference is a correct deep copy
    default DiscreteMeasurementAttributes copy(DiscreteMeasurementAttributes attributes, @Context ObjectMapper objectMapper) {
        if (attributes == null) {
            return null;
        }
        DiscreteMeasurementAttributes copy = new DiscreteMeasurementAttributes();
        copy.setId(attributes.getId());
        copy.setType(attributes.getType());
        copy.setTapChanger(attributes.getTapChanger());
        copy.setValueType(attributes.getValueType());
        if (attributes.getProperties() != null) {
            copy.setProperties(new HashMap<>(attributes.getProperties()));
        }
        copy.setValue(attributes.getValue());
        copy.setValid(attributes.isValid());
        return copy;
    }

    DynamicModelInfoAttributes copy(DynamicModelInfoAttributes attributes, @Context ObjectMapper objectMapper);

    GeneratorFortescueAttributes copy(GeneratorFortescueAttributes attributes, @Context ObjectMapper objectMapper);

    GeneratorStartupAttributes copy(GeneratorStartupAttributes attributes, @Context ObjectMapper objectMapper);

    InjectionObservabilityAttributes copy(InjectionObservabilityAttributes attributes, @Context ObjectMapper objectMapper);

    LegFortescueAttributes copy(LegFortescueAttributes attributes, @Context ObjectMapper objectMapper);

    LineFortescueAttributes copy(LineFortescueAttributes attributes, @Context ObjectMapper objectMapper);

    // hand written: Coordinate is an immutable powsybl-core class without setters
    default LinePositionAttributes copy(LinePositionAttributes attributes, @Context ObjectMapper objectMapper) {
        if (attributes == null) {
            return null;
        }
        List<Coordinate> coordinates = null;
        if (attributes.getCoordinates() != null) {
            coordinates = new ArrayList<>(attributes.getCoordinates().size());
            for (Coordinate coordinate : attributes.getCoordinates()) {
                coordinates.add(copyCoordinate(coordinate));
            }
        }
        return new LinePositionAttributes(coordinates);
    }

    private static Coordinate copyCoordinate(Coordinate coordinate) {
        return coordinate == null ? null : new Coordinate(coordinate.getLatitude(), coordinate.getLongitude());
    }

    LoadAsymmetricalAttributes copy(LoadAsymmetricalAttributes attributes, @Context ObjectMapper objectMapper);

    // hand written: a null list source must keep the field initializer of the no-args constructor
    // (an empty list), like the jackson copy: the field is omitted from the json when null, so the
    // setter is never called on deserialization
    default MeasurementsAttributes copy(MeasurementsAttributes attributes, @Context ObjectMapper objectMapper) {
        if (attributes == null) {
            return null;
        }
        MeasurementsAttributes copy = new MeasurementsAttributes();
        if (attributes.getMeasurementAttributes() != null) {
            List<MeasurementAttributes> measurements = new ArrayList<>(attributes.getMeasurementAttributes().size());
            for (MeasurementAttributes measurement : attributes.getMeasurementAttributes()) {
                measurements.add(copy(measurement, objectMapper));
            }
            copy.setMeasurementAttributes(measurements);
        }
        return copy;
    }

    MeasurementAttributes copy(MeasurementAttributes attributes, @Context ObjectMapper objectMapper);

    OperatingStatusAttributes copy(OperatingStatusAttributes attributes, @Context ObjectMapper objectMapper);

    // hand written: no setters (jackson populates the referencePriorities list through the getter)
    default ReferencePrioritiesAttributes copy(ReferencePrioritiesAttributes attributes, @Context ObjectMapper objectMapper) {
        if (attributes == null) {
            return null;
        }
        ReferencePrioritiesAttributes copy = new ReferencePrioritiesAttributes();
        if (attributes.getReferencePriorities() != null) {
            for (ReferencePriorityAttributes referencePriority : attributes.getReferencePriorities()) {
                copy.getReferencePriorities().add(copy(referencePriority, objectMapper));
            }
        }
        return copy;
    }

    ReferencePriorityAttributes copy(ReferencePriorityAttributes attributes, @Context ObjectMapper objectMapper);

    SecondaryVoltageControlAttributes copy(SecondaryVoltageControlAttributes attributes, @Context ObjectMapper objectMapper);

    // hand written: Coordinate is an immutable powsybl-core class without setters
    default SubstationPositionAttributes copy(SubstationPositionAttributes attributes, @Context ObjectMapper objectMapper) {
        if (attributes == null) {
            return null;
        }
        return new SubstationPositionAttributes(copyCoordinate(attributes.getCoordinate()));
    }

    ThreeWindingsTransformerFortescueAttributes copy(ThreeWindingsTransformerFortescueAttributes attributes, @Context ObjectMapper objectMapper);

    ThreeWindingsTransformerToBeEstimatedAttributes copy(ThreeWindingsTransformerToBeEstimatedAttributes attributes, @Context ObjectMapper objectMapper);

    TwoWindingsTransformerFortescueAttributes copy(TwoWindingsTransformerFortescueAttributes attributes, @Context ObjectMapper objectMapper);

    TwoWindingsTransformerToBeEstimatedAttributes copy(TwoWindingsTransformerToBeEstimatedAttributes attributes, @Context ObjectMapper objectMapper);

    VoltageRegulationAttributes copy(VoltageRegulationAttributes attributes, @Context ObjectMapper objectMapper);
}
