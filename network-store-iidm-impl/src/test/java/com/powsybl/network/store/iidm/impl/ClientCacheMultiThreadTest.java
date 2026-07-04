/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl;

import com.powsybl.network.store.model.LoadAttributes;
import com.powsybl.network.store.model.Resource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Concurrent access to the client caches by threads working on distinct variants: reads stay parallel across
 * variants, and a collection is loaded from the server only once per variant even under concurrent access.
 */
class ClientCacheMultiThreadTest {

    private static final UUID NETWORK_UUID = UUID.randomUUID();

    private static final int VARIANT_COUNT = 2;
    private static final int THREADS_PER_VARIANT = 4;
    private static final int ITERATIONS = 100;

    private static Resource<LoadAttributes> createLoadResource(String id, int variantNum) {
        return Resource.loadBuilder()
                .id(id)
                .variantNum(variantNum)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .build())
                .build();
    }

    private static List<Resource<LoadAttributes>> variantLoads(int variantNum) {
        return List.of(createLoadResource("l1", variantNum), createLoadResource("l2", variantNum));
    }

    private void runConcurrently(int threadCount, ThrowingTask task) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threadCount; t++) {
                int threadIndex = t;
                futures.add(executor.submit(() -> {
                    barrier.await();
                    task.run(threadIndex);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private interface ThrowingTask {
        void run(int threadIndex) throws Exception;
    }

    @Test
    void collectionCacheIsLoadedOnlyOncePerVariantUnderConcurrentReads() throws Exception {
        AtomicInteger[] allLoaderCallsPerVariant = new AtomicInteger[VARIANT_COUNT];
        List<CollectionCache<LoadAttributes>> cachePerVariant = new ArrayList<>();
        for (int v = 0; v < VARIANT_COUNT; v++) {
            allLoaderCallsPerVariant[v] = new AtomicInteger();
            cachePerVariant.add(new CollectionCache<>(
                (networkUuid, variantNum, id) -> Optional.of(createLoadResource(id, variantNum)),
                null,
                (networkUuid, variantNum) -> {
                    allLoaderCallsPerVariant[variantNum].incrementAndGet();
                    // widen the race window: a concurrent thread on the same variant must wait, not load again
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return variantLoads(variantNum);
                },
                new MockNetworkStoreClient()));
        }

        runConcurrently(VARIANT_COUNT * THREADS_PER_VARIANT, threadIndex -> {
            int variantNum = threadIndex % VARIANT_COUNT;
            CollectionCache<LoadAttributes> cache = cachePerVariant.get(variantNum);
            for (int i = 0; i < ITERATIONS; i++) {
                List<Resource<LoadAttributes>> resources = cache.getResources(NETWORK_UUID, variantNum);
                assertEquals(2, resources.size());
                for (Resource<LoadAttributes> resource : resources) {
                    assertEquals(variantNum, resource.getVariantNum());
                }
                assertEquals(variantNum, cache.getResource(NETWORK_UUID, variantNum, "l1").orElseThrow().getVariantNum());
            }
        });

        for (int v = 0; v < VARIANT_COUNT; v++) {
            assertEquals(1, allLoaderCallsPerVariant[v].get(), "collection of variant " + v + " should be loaded only once");
        }
    }

    @Test
    void cachedClientLoadsEachVariantOnlyOnceUnderConcurrentReads() throws Exception {
        NetworkStoreClient delegate = Mockito.mock(NetworkStoreClient.class);
        when(delegate.getLoads(eq(NETWORK_UUID), anyInt())).thenAnswer(invocation -> {
            Thread.sleep(50);
            return variantLoads(invocation.getArgument(1));
        });
        CachedNetworkStoreClient cachedClient = new CachedNetworkStoreClient(delegate);

        runConcurrently(VARIANT_COUNT * THREADS_PER_VARIANT, threadIndex -> {
            int variantNum = threadIndex % VARIANT_COUNT;
            for (int i = 0; i < ITERATIONS; i++) {
                assertEquals(2, cachedClient.getLoads(NETWORK_UUID, variantNum).size());
                assertEquals(variantNum, cachedClient.getLoad(NETWORK_UUID, variantNum, "l1").orElseThrow().getVariantNum());
            }
        });

        for (int v = 0; v < VARIANT_COUNT; v++) {
            verify(delegate, times(1)).getLoads(NETWORK_UUID, v);
        }
    }
}
