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
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference terminals of the network for one variant (the network resource is per variant, so the
 * per variant semantics of the extension comes for free, including variant cloning).
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Reference terminals attributes")
public class ReferenceTerminalsAttributes implements ExtensionAttributes {

    @Schema(description = "Reference terminals")
    @Builder.Default
    private List<TerminalRefAttributes> terminalRefs = new ArrayList<>();
}
