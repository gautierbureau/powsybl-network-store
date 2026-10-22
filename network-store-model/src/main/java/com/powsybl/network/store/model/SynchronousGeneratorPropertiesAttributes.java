/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.powsybl.dynawo.extensions.api.generator.RpclType;
import com.powsybl.dynawo.extensions.api.generator.SynchronousGeneratorProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The controls of a synchronous generator that powsybl-dynawo models it from.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Synchronous generator properties attributes")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SynchronousGeneratorPropertiesAttributes implements ExtensionAttributes {

    @Schema(description = "Number of windings")
    private SynchronousGeneratorProperties.Windings numberOfWindings;

    @Schema(description = "Governor")
    private String governor;

    @Schema(description = "Voltage regulator")
    private String voltageRegulator;

    @Schema(description = "Power system stabilizer")
    private String pss;

    @Schema(description = "Auxiliaries")
    private boolean auxiliaries;

    @Schema(description = "Internal transformer")
    private boolean internalTransformer;

    @Schema(description = "Reactive power control loop")
    private RpclType rpcl;

    @Schema(description = "Under voltage automaton")
    private SynchronousGeneratorProperties.Uva uva;

    @Schema(description = "Aggregated")
    private boolean aggregated;

    @Schema(description = "Reactive power limits")
    private boolean qlim;
}
