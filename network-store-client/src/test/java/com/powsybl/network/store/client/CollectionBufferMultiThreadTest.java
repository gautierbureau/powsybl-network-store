/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.client;

import com.powsybl.network.store.model.LoadAttributes;
import com.powsybl.network.store.model.Resource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * Concurrent writes to the same collection buffer: appends are serialized on the buffer instance, nothing is lost
 * and the flush sees a consistent state.
 */
public class CollectionBufferMultiThreadTest {

    private static final UUID NETWORK_UUID = UUID.randomUUID();

    private static final int THREAD_COUNT = 4;
    private static final int RESOURCES_PER_THREAD = 200;

    private static Resource<LoadAttributes> createLoadResource(String id) {
        return Resource.loadBuilder()
                .id(id)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .build())
                .build();
    }

    @Test
    public void concurrentWritesToTheSameBufferAreNotLost() throws Exception {
        List<Resource<LoadAttributes>> createdOnServer = new CopyOnWriteArrayList<>();
        List<Resource<LoadAttributes>> updatedOnServer = new CopyOnWriteArrayList<>();
        List<String> removedOnServer = new CopyOnWriteArrayList<>();

        CollectionBuffer<LoadAttributes> buffer = new CollectionBuffer<>(
            (networkUuid, resources) -> createdOnServer.addAll(resources),
            (networkUuid, resources, attributeFilter) -> updatedOnServer.addAll(resources),
            (networkUuid, variantNum, ids) -> removedOnServer.addAll(ids));

        CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREAD_COUNT; t++) {
                int threadIndex = t;
                futures.add(executor.submit(() -> {
                    barrier.await();
                    for (int i = 0; i < RESOURCES_PER_THREAD; i++) {
                        String id = "l-" + threadIndex + "-" + i;
                        buffer.create(createLoadResource(id));
                        if (i % 2 == 0) {
                            // remove of a buffered creation just drops it from the buffer
                            buffer.remove(id);
                        }
                        // updates of resources created by other rounds
                        buffer.update(createLoadResource("u-" + threadIndex + "-" + i));
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        buffer.flush(NETWORK_UUID, Resource.INITIAL_VARIANT_NUM);

        // every thread keeps the odd-indexed creations only, and all updates
        assertEquals(THREAD_COUNT * RESOURCES_PER_THREAD / 2, createdOnServer.size());
        assertEquals(THREAD_COUNT * RESOURCES_PER_THREAD, updatedOnServer.size());
        assertEquals(0, removedOnServer.size());

        Set<String> expectedCreatedIds = new HashSet<>();
        for (int t = 0; t < THREAD_COUNT; t++) {
            for (int i = 1; i < RESOURCES_PER_THREAD; i += 2) {
                expectedCreatedIds.add("l-" + t + "-" + i);
            }
        }
        Set<String> createdIds = new HashSet<>();
        createdOnServer.forEach(resource -> createdIds.add(resource.getId()));
        assertEquals(expectedCreatedIds, createdIds);

        // flush is terminal: buffers are emptied
        assertEquals(0, buffer.getCreateResourcesIds().size());
        assertEquals(0, buffer.getRemoveResourcesIds().size());
    }
}
