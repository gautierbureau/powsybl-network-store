/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl.extensions;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.extensions.ReferenceTerminals;
import com.powsybl.network.store.iidm.impl.NetworkImpl;
import com.powsybl.network.store.iidm.impl.TerminalRefUtils;
import com.powsybl.network.store.model.ReferenceTerminalsAttributes;
import com.powsybl.network.store.model.TerminalRefAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The reference terminals are stored as terminal references (connectable id and side) in the
 * network resource extension attributes: the resource is per variant, so each variant has its own
 * reference terminals and variant cloning clones them, like any other attribute. Terminals whose
 * connectable has been removed from the network are filtered out when reading.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public class ReferenceTerminalsImpl extends AbstractExtension<Network> implements ReferenceTerminals {

    public ReferenceTerminalsImpl(Network network) {
        super(network);
    }

    private NetworkImpl getNetwork() {
        return (NetworkImpl) getExtendable();
    }

    /**
     * The extension attributes of the current variant, or null when the extension was created
     * while another variant was the working one (each variant has its own network resource): such
     * a variant simply has no reference terminals yet.
     */
    private ReferenceTerminalsAttributes getAttributes() {
        return (ReferenceTerminalsAttributes) getNetwork().getResource().getAttributes()
                .getExtensionAttributes().get(ReferenceTerminals.NAME);
    }

    private List<TerminalRefAttributes> getTerminalRefs() {
        ReferenceTerminalsAttributes attributes = getAttributes();
        return attributes != null ? attributes.getTerminalRefs() : Collections.emptyList();
    }

    private static ReferenceTerminalsAttributes getOrCreateAttributes(com.powsybl.network.store.model.Resource<com.powsybl.network.store.model.NetworkAttributes> res) {
        return (ReferenceTerminalsAttributes) res.getAttributes().getExtensionAttributes()
                .computeIfAbsent(ReferenceTerminals.NAME, name -> ReferenceTerminalsAttributes.builder().build());
    }

    static void checkTerminalInNetwork(Terminal terminal, Network network) {
        if (!terminal.getVoltageLevel().getNetwork().equals(network)) {
            throw new PowsyblException("Terminal given is not in the right Network ("
                    + terminal.getVoltageLevel().getNetwork().getId() + " instead of " + network.getId() + ")");
        }
    }

    @Override
    public Set<Terminal> getReferenceTerminals() {
        Set<Terminal> terminals = new LinkedHashSet<>();
        for (TerminalRefAttributes terminalRef : getTerminalRefs()) {
            // connectables removed from the network since the terminal was recorded are skipped
            if (getNetwork().getIndex().getIdentifiable(terminalRef.getConnectableId()) != null) {
                terminals.add(TerminalRefUtils.getTerminal(getNetwork().getIndex(), terminalRef));
            }
        }
        return Collections.unmodifiableSet(terminals);
    }

    @Override
    public void setReferenceTerminals(Set<Terminal> terminals) {
        Objects.requireNonNull(terminals);
        terminals.forEach(terminal -> checkTerminalInNetwork(terminal, getExtendable()));
        List<TerminalRefAttributes> terminalRefs = new ArrayList<>();
        for (Terminal terminal : terminals) {
            TerminalRefAttributes terminalRef = TerminalRefUtils.getTerminalRefAttributes(terminal);
            if (!terminalRefs.contains(terminalRef)) {
                terminalRefs.add(terminalRef);
            }
        }
        getNetwork().updateResourceWithoutNotification(res -> getOrCreateAttributes(res).setTerminalRefs(terminalRefs));
    }

    @Override
    public ReferenceTerminals reset() {
        setReferenceTerminals(Collections.emptySet());
        return this;
    }

    @Override
    public ReferenceTerminals addReferenceTerminal(Terminal terminal) {
        Objects.requireNonNull(terminal);
        checkTerminalInNetwork(terminal, getExtendable());
        TerminalRefAttributes terminalRef = TerminalRefUtils.getTerminalRefAttributes(terminal);
        if (!getTerminalRefs().contains(terminalRef)) {
            getNetwork().updateResourceWithoutNotification(res -> getOrCreateAttributes(res).getTerminalRefs().add(terminalRef));
        }
        return this;
    }
}
