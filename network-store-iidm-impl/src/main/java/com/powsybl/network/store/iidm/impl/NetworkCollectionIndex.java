/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl;

import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
public class NetworkCollectionIndex<C> {

    // concurrent so that threads working on distinct variants can resolve their collection in parallel
    private final Map<Pair<UUID, Integer>, C> collections = new ConcurrentHashMap<>();

    private final Supplier<C> factory;

    public NetworkCollectionIndex(Supplier<C> factory) {
        this.factory = Objects.requireNonNull(factory);
    }

    public C getCollection(UUID networkUuid, int variantNum) {
        Objects.requireNonNull(networkUuid);
        return collections.computeIfAbsent(Pair.of(networkUuid, variantNum), p -> factory.get());
    }

    public void addCollection(UUID networkUuid, int variantNum, C collection) {
        collections.put(Pair.of(networkUuid, variantNum), collection);
    }

    public void removeCollection(UUID networkUuid, int variantNum) {
        Objects.requireNonNull(networkUuid);
        collections.remove(Pair.of(networkUuid, variantNum));
    }

    public void removeCollection(UUID networkUuid) {
        collections.keySet().removeIf(p -> p.getLeft().equals(networkUuid));
    }

    public void applyToCollection(UUID networkUuid, BiConsumer<Integer, C> fct) {
        // sorted by variant num to keep a deterministic iteration order, as the concurrent map does not preserve
        // the insertion order the previous implementation had
        collections.entrySet().stream()
                .filter(e -> e.getKey().getLeft().equals(networkUuid))
                .sorted(Comparator.comparingInt(e -> e.getKey().getRight()))
                .forEach(e -> fct.accept(e.getKey().getRight(), e.getValue()));
    }
}
