/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl.extensions;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionAdderProvider;
import com.powsybl.dynawo.extensions.api.generator.SynchronousGeneratorProperties;
import com.powsybl.iidm.network.Generator;

/**
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
@AutoService(ExtensionAdderProvider.class)
public class SynchronousGeneratorPropertiesAdderImplProvider
        implements ExtensionAdderProvider<Generator, SynchronousGeneratorProperties, SynchronousGeneratorPropertiesAdderImpl> {

    @Override
    public String getImplementationName() {
        return "NetworkStore";
    }

    @Override
    public String getExtensionName() {
        return SynchronousGeneratorProperties.NAME;
    }

    @Override
    public Class<SynchronousGeneratorPropertiesAdderImpl> getAdderClass() {
        return SynchronousGeneratorPropertiesAdderImpl.class;
    }

    @Override
    public SynchronousGeneratorPropertiesAdderImpl newAdder(Generator extendable) {
        return new SynchronousGeneratorPropertiesAdderImpl(extendable);
    }
}
