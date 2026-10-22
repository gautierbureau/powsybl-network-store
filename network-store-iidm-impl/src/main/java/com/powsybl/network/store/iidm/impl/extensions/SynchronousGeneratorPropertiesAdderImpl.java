/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl.extensions;

import com.powsybl.commons.PowsyblException;
import com.powsybl.dynawo.extensions.api.generator.RpclType;
import com.powsybl.dynawo.extensions.api.generator.SynchronousGeneratorProperties;
import com.powsybl.dynawo.extensions.api.generator.SynchronousGeneratorPropertiesAdder;
import com.powsybl.iidm.network.Generator;
import com.powsybl.network.store.iidm.impl.GeneratorImpl;
import com.powsybl.network.store.model.SynchronousGeneratorPropertiesAttributes;

import java.util.Objects;

/**
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public class SynchronousGeneratorPropertiesAdderImpl extends AbstractIidmExtensionAdder<Generator, SynchronousGeneratorProperties> implements SynchronousGeneratorPropertiesAdder {

    private SynchronousGeneratorProperties.Windings numberOfWindings;

    private String governor;

    private String voltageRegulator;

    private String pss;

    private boolean auxiliaries;

    private boolean internalTransformer;

    private RpclType rpcl = RpclType.NONE;

    private SynchronousGeneratorProperties.Uva uva = SynchronousGeneratorProperties.Uva.LOCAL;

    private boolean aggregated;

    private boolean qlim;

    public SynchronousGeneratorPropertiesAdderImpl(Generator generator) {
        super(generator);
    }

    @Override
    protected SynchronousGeneratorProperties createExtension(Generator generator) {
        Objects.requireNonNull(numberOfWindings, "numberOfWindings");
        if (governor == null ^ voltageRegulator == null) {
            throw new PowsyblException("Governor and Voltage regulator must be both instantiated or both null");
        }
        var attributes = new SynchronousGeneratorPropertiesAttributes(numberOfWindings, governor, voltageRegulator, pss,
                auxiliaries, internalTransformer, rpcl, uva, aggregated, qlim);
        ((GeneratorImpl) generator).updateResourceWithoutNotification(res -> res.getAttributes().getExtensionAttributes().put(SynchronousGeneratorProperties.NAME, attributes));
        return new SynchronousGeneratorPropertiesImpl((GeneratorImpl) generator);
    }

    @Override
    public SynchronousGeneratorPropertiesAdderImpl withNumberOfWindings(SynchronousGeneratorProperties.Windings numberOfWindings) {
        this.numberOfWindings = numberOfWindings;
        return this;
    }

    @Override
    public SynchronousGeneratorPropertiesAdderImpl withGovernor(String governor) {
        this.governor = governor;
        return this;
    }

    @Override
    public SynchronousGeneratorPropertiesAdderImpl withVoltageRegulator(String voltageRegulator) {
        this.voltageRegulator = voltageRegulator;
        return this;
    }

    @Override
    public SynchronousGeneratorPropertiesAdderImpl withPss(String pss) {
        this.pss = pss;
        return this;
    }

    @Override
    public SynchronousGeneratorPropertiesAdderImpl withAuxiliaries(boolean auxiliaries) {
        this.auxiliaries = auxiliaries;
        return this;
    }

    @Override
    public SynchronousGeneratorPropertiesAdderImpl withInternalTransformer(boolean internalTransformer) {
        this.internalTransformer = internalTransformer;
        return this;
    }

    @Override
    public SynchronousGeneratorPropertiesAdderImpl withRpcl(RpclType rpcl) {
        this.rpcl = Objects.requireNonNull(rpcl, "rpcl");
        return this;
    }

    @Override
    public SynchronousGeneratorPropertiesAdderImpl withUva(SynchronousGeneratorProperties.Uva uva) {
        this.uva = Objects.requireNonNull(uva, "uva");
        return this;
    }

    @Override
    public SynchronousGeneratorPropertiesAdderImpl withAggregated(boolean aggregated) {
        this.aggregated = aggregated;
        return this;
    }

    @Override
    public SynchronousGeneratorPropertiesAdderImpl withQlim(boolean qlim) {
        this.qlim = qlim;
        return this;
    }
}
