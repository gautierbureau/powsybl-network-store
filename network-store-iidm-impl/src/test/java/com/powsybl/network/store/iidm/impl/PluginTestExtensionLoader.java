/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.iidm.network.Injection;
import com.powsybl.network.store.model.ExtensionLoader;

/**
 * Loader of {@link PluginTestExtensionAttributes}, mimicking the loader a third-party plugin
 * registers so its extension attributes can be (de)serialized.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@AutoService(ExtensionLoader.class)
public class PluginTestExtensionLoader<I extends Injection<I>> implements ExtensionLoader<I, Extension<I>, PluginTestExtensionAttributes> {

    @Override
    public Extension<I> load(I injection) {
        throw new UnsupportedOperationException("Not needed to test the attributes copy");
    }

    @Override
    public String getName() {
        return "pluginTestExtension";
    }

    @Override
    public Class<Extension> getType() {
        return Extension.class;
    }

    @Override
    public Class<PluginTestExtensionAttributes> getAttributesType() {
        return PluginTestExtensionAttributes.class;
    }
}
