/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * All the pending modifications of one variant of a network, flushed to the server in a single
 * request (see the bulk update endpoint) instead of one request per resource type and operation.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Bulk update of one variant of a network")
public class BulkUpdateBundle {

    @Schema(description = "Bulk update operations, applied in order")
    @Builder.Default
    private List<BulkUpdateEntry> entries = new ArrayList<>();
}
