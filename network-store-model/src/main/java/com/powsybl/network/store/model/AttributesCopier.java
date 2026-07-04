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
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.control.DeepClone;
import org.mapstruct.factory.Mappers;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Structural (generated) deep copy of the resource attributes, used to clone resources to a new
 * variant without a Jackson serialization round trip. The copy methods are generated at compile
 * time by MapStruct from the getters and setters, so a new field is picked up automatically at
 * the next build.
 *
 * <p>The only part of the attributes graph that is not copied structurally is the extension
 * attributes: their concrete classes are resolved at runtime through {@link ExtensionLoaders}
 * (plugins can register new ones), so an open set of subtypes that cannot be enumerated at
 * compile time. They fall back to a Jackson {@link TokenBuffer} round trip, exactly the copy the
 * previous implementation applied to the whole resource.
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
     * Extension attributes classes form an open set (plugins register them through
     * {@link ExtensionLoaders}), so they cannot be copied structurally: fall back to the Jackson
     * copy, going through a token buffer to skip the text encoding and parsing.
     */
    default ExtensionAttributes copy(ExtensionAttributes attributes, @Context ObjectMapper objectMapper) {
        if (attributes == null) {
            return null;
        }
        try (TokenBuffer buffer = new TokenBuffer(objectMapper, false)) {
            objectMapper.writeValue(buffer, attributes);
            return objectMapper.readValue(buffer.asParser(), ExtensionAttributes.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
