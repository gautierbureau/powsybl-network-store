/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl.extensions;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.extensions.ReferenceTerminals;
import com.powsybl.iidm.network.extensions.ReferenceTerminalsAdder;
import com.powsybl.network.store.iidm.impl.NetworkImpl;
import com.powsybl.network.store.iidm.impl.TerminalRefUtils;
import com.powsybl.network.store.model.ReferenceTerminalsAttributes;
import com.powsybl.network.store.model.TerminalRefAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public class ReferenceTerminalsAdderImpl extends AbstractIidmExtensionAdder<Network, ReferenceTerminals> implements ReferenceTerminalsAdder {

    private Set<Terminal> terminals;

    public ReferenceTerminalsAdderImpl(Network extendable) {
        super(extendable);
    }

    @Override
    public ReferenceTerminalsAdder withTerminals(Set<Terminal> terminals) {
        this.terminals = terminals;
        return this;
    }

    @Override
    protected ReferenceTerminals createExtension(Network network) {
        if (terminals == null) {
            throw new PowsyblException("Terminals needs to be set to create ReferenceTerminals extension");
        }
        terminals.forEach(terminal -> ReferenceTerminalsImpl.checkTerminalInNetwork(terminal, network));
        List<TerminalRefAttributes> terminalRefs = new ArrayList<>();
        for (Terminal terminal : terminals) {
            TerminalRefAttributes terminalRef = TerminalRefUtils.getTerminalRefAttributes(terminal);
            if (!terminalRefs.contains(terminalRef)) {
                terminalRefs.add(terminalRef);
            }
        }
        var attributes = ReferenceTerminalsAttributes.builder()
                .terminalRefs(terminalRefs)
                .build();
        ((NetworkImpl) network).updateResourceWithoutNotification(res ->
                res.getAttributes().getExtensionAttributes().put(ReferenceTerminals.NAME, attributes));
        return new ReferenceTerminalsImpl(network);
    }
}
