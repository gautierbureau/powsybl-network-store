/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl.extensions;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.dynawo.extensions.api.generator.SynchronousGeneratorProperties;
import com.powsybl.iidm.network.Generator;
import com.powsybl.network.store.iidm.impl.GeneratorImpl;
import com.powsybl.network.store.model.ExtensionLoader;
import com.powsybl.network.store.model.SynchronousGeneratorPropertiesAttributes;

/**
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
@AutoService(ExtensionLoader.class)
public class SynchronousGeneratorPropertiesLoader implements ExtensionLoader<Generator, SynchronousGeneratorProperties, SynchronousGeneratorPropertiesAttributes> {
    @Override
    public Extension<Generator> load(Generator generator) {
        return new SynchronousGeneratorPropertiesImpl((GeneratorImpl) generator);
    }

    @Override
    public String getName() {
        return SynchronousGeneratorProperties.NAME;
    }

    @Override
    public Class<SynchronousGeneratorProperties> getType() {
        return SynchronousGeneratorProperties.class;
    }

    @Override
    public Class<SynchronousGeneratorPropertiesAttributes> getAttributesType() {
        return SynchronousGeneratorPropertiesAttributes.class;
    }
}
