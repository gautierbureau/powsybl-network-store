/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One operation of a {@link BulkUpdateBundle}: the pending creations, updates or removals of one
 * resource type. The body is pre-serialized by the sender with the json view matching the
 * attribute filter (and, for the subset filters like SV, with the filter set on each resource, as
 * the per type endpoints expect), so that a single bundle can carry entries with distinct views.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "One bulk update operation")
public class BulkUpdateEntry {

    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String REMOVE = "REMOVE";

    @Schema(description = "Resource type")
    private ResourceType resourceType;

    @Schema(description = "Operation: CREATE, UPDATE or REMOVE")
    private String operation;

    @Schema(description = "Attribute filter name for updates routed to a filtered endpoint (e.g. SV), null otherwise")
    private String attributeFilter;

    @Schema(description = "For CREATE/UPDATE: json array of resources; for REMOVE: json array of resource ids")
    private JsonNode body;
}
