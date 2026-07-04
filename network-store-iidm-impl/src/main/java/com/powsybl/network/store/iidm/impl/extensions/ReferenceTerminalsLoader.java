/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl.extensions;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.ReferenceTerminals;
import com.powsybl.network.store.model.ExtensionLoader;
import com.powsybl.network.store.model.ReferenceTerminalsAttributes;

/**
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
@AutoService(ExtensionLoader.class)
public class ReferenceTerminalsLoader implements ExtensionLoader<Network, ReferenceTerminals, ReferenceTerminalsAttributes> {
    @Override
    public Extension<Network> load(Network network) {
        return new ReferenceTerminalsImpl(network);
    }

    @Override
    public String getName() {
        return ReferenceTerminals.NAME;
    }

    @Override
    public Class<ReferenceTerminals> getType() {
        return ReferenceTerminals.class;
    }

    @Override
    public Class<ReferenceTerminalsAttributes> getAttributesType() {
        return ReferenceTerminalsAttributes.class;
    }
}
