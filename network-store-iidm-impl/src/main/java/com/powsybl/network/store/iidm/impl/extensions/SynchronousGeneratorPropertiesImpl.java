/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.dynawo.extensions.api.generator.RpclType;
import com.powsybl.dynawo.extensions.api.generator.SynchronousGeneratorProperties;
import com.powsybl.iidm.network.Generator;
import com.powsybl.network.store.iidm.impl.GeneratorImpl;
import com.powsybl.network.store.model.GeneratorAttributes;
import com.powsybl.network.store.model.Resource;
import com.powsybl.network.store.model.SynchronousGeneratorPropertiesAttributes;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public class SynchronousGeneratorPropertiesImpl extends AbstractExtension<Generator> implements SynchronousGeneratorProperties {

    public SynchronousGeneratorPropertiesImpl(GeneratorImpl generator) {
        super(generator);
    }

    private GeneratorImpl getGenerator() {
        return (GeneratorImpl) getExtendable();
    }

    private static SynchronousGeneratorPropertiesAttributes getAttributes(Resource<GeneratorAttributes> resource) {
        return (SynchronousGeneratorPropertiesAttributes) resource.getAttributes().getExtensionAttributes().get(SynchronousGeneratorProperties.NAME);
    }

    private SynchronousGeneratorPropertiesAttributes getAttributes() {
        return getAttributes(getGenerator().getResource());
    }

    private <T> void update(String attribute, T oldValue, T newValue, BiConsumer<SynchronousGeneratorPropertiesAttributes, T> setter) {
        if (!Objects.equals(oldValue, newValue)) {
            getGenerator().updateResourceExtension(this, res -> setter.accept(getAttributes(res), newValue), attribute, oldValue, newValue);
        }
    }

    @Override
    public Windings getNumberOfWindings() {
        return getAttributes().getNumberOfWindings();
    }

    @Override
    public void setNumberOfWindings(Windings numberOfWindings) {
        update("numberOfWindings", getNumberOfWindings(), Objects.requireNonNull(numberOfWindings, "numberOfWindings"),
                SynchronousGeneratorPropertiesAttributes::setNumberOfWindings);
    }

    @Override
    public String getGovernor() {
        return getAttributes().getGovernor();
    }

    @Override
    public void setGovernor(String governor) {
        update("governor", getGovernor(), governor, SynchronousGeneratorPropertiesAttributes::setGovernor);
    }

    @Override
    public String getVoltageRegulator() {
        return getAttributes().getVoltageRegulator();
    }

    @Override
    public void setVoltageRegulator(String voltageRegulator) {
        update("voltageRegulator", getVoltageRegulator(), voltageRegulator, SynchronousGeneratorPropertiesAttributes::setVoltageRegulator);
    }

    @Override
    public String getPss() {
        return getAttributes().getPss();
    }

    @Override
    public void setPss(String pss) {
        update("pss", getPss(), pss, SynchronousGeneratorPropertiesAttributes::setPss);
    }

    @Override
    public boolean isAuxiliaries() {
        return getAttributes().isAuxiliaries();
    }

    @Override
    public void setAuxiliaries(boolean auxiliaries) {
        update("auxiliaries", isAuxiliaries(), auxiliaries, SynchronousGeneratorPropertiesAttributes::setAuxiliaries);
    }

    @Override
    public boolean isInternalTransformer() {
        return getAttributes().isInternalTransformer();
    }

    @Override
    public void setInternalTransformer(boolean internalTransformer) {
        update("internalTransformer", isInternalTransformer(), internalTransformer, SynchronousGeneratorPropertiesAttributes::setInternalTransformer);
    }

    @Override
    public boolean isRpcl1() {
        return getRpcl().isRpcl1();
    }

    @Override
    public boolean isRpcl2() {
        return getRpcl().isRpcl2();
    }

    @Override
    public RpclType getRpcl() {
        return getAttributes().getRpcl();
    }

    @Override
    public void setRpcl(RpclType rpcl) {
        update("rpcl", getRpcl(), rpcl, SynchronousGeneratorPropertiesAttributes::setRpcl);
    }

    @Override
    public Uva getUva() {
        return getAttributes().getUva();
    }

    @Override
    public void setUva(Uva uva) {
        update("uva", getUva(), uva, SynchronousGeneratorPropertiesAttributes::setUva);
    }

    @Override
    public boolean isAggregated() {
        return getAttributes().isAggregated();
    }

    @Override
    public void setAggregated(boolean aggregated) {
        update("aggregated", isAggregated(), aggregated, SynchronousGeneratorPropertiesAttributes::setAggregated);
    }

    @Override
    public boolean isQlim() {
        return getAttributes().isQlim();
    }

    @Override
    public void setQlim(boolean qlim) {
        update("qlim", isQlim(), qlim, SynchronousGeneratorPropertiesAttributes::setQlim);
    }
}
