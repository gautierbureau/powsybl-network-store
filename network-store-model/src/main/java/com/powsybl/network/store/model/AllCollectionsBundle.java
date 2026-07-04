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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * All the identifiable collections of one network variant, plus the selected operational limits
 * groups of the branches, fetched from the server in a single round trip (see the
 * {@code /networks/{networkUuid}/{variantNum}/collections} endpoint). Used by the preloading
 * client to fill its caches upfront without one request per collection.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@Schema(description = "All the identifiable collections of a network variant")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllCollectionsBundle {

    @Schema(description = "Resources by resource type")
    private Map<ResourceType, List<Resource<IdentifiableAttributes>>> resources;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Selected operational limits group attributes by resource type, branch id, side and group id")
    private Map<ResourceType, Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>>> selectedOperationalLimitsGroups;
}
