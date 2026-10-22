/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.network.store.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A bus of the bus/breaker view of a pilot point, by the id of its voltage level and its id
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Pilot point bus attributes")
public class PilotPointBusAttributes {
    private String voltageLevelId;
    private String busId;
}
