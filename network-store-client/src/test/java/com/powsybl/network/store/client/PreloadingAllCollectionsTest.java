/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.client;

import com.powsybl.network.store.iidm.impl.CachedNetworkStoreClient;
import com.powsybl.network.store.iidm.impl.OfflineNetworkStoreClient;
import com.powsybl.network.store.model.ExtensionAttributes;
import com.powsybl.network.store.model.ResourceType;
import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class PreloadingAllCollectionsTest {

    @Test
    public void test() {
        var client = new PreloadingNetworkStoreClient(new CachedNetworkStoreClient(new OfflineNetworkStoreClient()), PreloadingStrategy.COLLECTION, ForkJoinPool.commonPool());
        UUID networkUuid = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
        client.getSubstations(networkUuid, 0);
        assertTrue(client.isResourceTypeCached(networkUuid, 0, ResourceType.SUBSTATION));
        for (ResourceType resourceType : ResourceType.values()) {
            if (resourceType != ResourceType.SUBSTATION) {
                assertFalse(client.isResourceTypeCached(networkUuid, 0, ResourceType.GENERATOR));
            }
        }
    }

    @Test
    public void testWithAllCollections() {
        var client = new PreloadingNetworkStoreClient(new CachedNetworkStoreClient(new OfflineNetworkStoreClient()), PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW, ForkJoinPool.commonPool());
        UUID networkUuid = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
        client.getSubstations(networkUuid, 0);
        for (ResourceType resourceType : ResourceType.values()) {
            if (PreloadingNetworkStoreClient.RESOURCE_TYPES_NEEDED_FOR_BUS_VIEW.contains(resourceType)) {
                assertTrue(client.isResourceTypeCached(networkUuid, 0, resourceType));
            } else {
                assertFalse(client.isResourceTypeCached(networkUuid, 0, resourceType));
            }
        }
    }

    @Test
    public void testWithAllCollectionsNeededForComputation() {
        var client = new PreloadingNetworkStoreClient(new CachedNetworkStoreClient(new OfflineNetworkStoreClient()),
            PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_COMPUTATION, ForkJoinPool.commonPool());
        UUID networkUuid = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
        client.getSubstations(networkUuid, 0);
        for (ResourceType resourceType : ResourceType.values()) {
            if (PreloadingNetworkStoreClient.RESOURCE_TYPES_NEEDED_FOR_COMPUTATION.contains(resourceType)) {
                assertTrue(client.isResourceTypeCached(networkUuid, 0, resourceType));
            } else {
                assertFalse(client.isResourceTypeCached(networkUuid, 0, resourceType));
            }
        }
        assertTrue(PreloadingNetworkStoreClient.RESOURCE_TYPES_NEEDED_FOR_COMPUTATION.contains(ResourceType.SWITCH));
        assertTrue(PreloadingNetworkStoreClient.RESOURCE_TYPES_NEEDED_FOR_COMPUTATION.contains(ResourceType.CONFIGURED_BUS));
    }

    /**
     * An offline client counting the extension attributes loading calls, to check what the preloading
     * triggers (the bulk per-type loads run concurrently on the preloading executor, hence the
     * synchronized counters).
     */
    private static final class ExtensionCountingClient extends OfflineNetworkStoreClient {

        private final Map<ResourceType, Integer> bulkLoadsByType = new EnumMap<>(ResourceType.class);

        private int byNameLoads = 0;

        private int byIdentifiableIdLoads = 0;

        private int singleLoads = 0;

        @Override
        public synchronized Map<String, Map<String, ExtensionAttributes>> getAllExtensionsAttributesByResourceType(UUID networkUuid, int variantNum, ResourceType resourceType) {
            bulkLoadsByType.merge(resourceType, 1, Integer::sum);
            return super.getAllExtensionsAttributesByResourceType(networkUuid, variantNum, resourceType);
        }

        @Override
        public synchronized Map<String, ExtensionAttributes> getAllExtensionsAttributesByResourceTypeAndExtensionName(UUID uuid, int variantNum, ResourceType resourceType, String extensionName) {
            byNameLoads++;
            return super.getAllExtensionsAttributesByResourceTypeAndExtensionName(uuid, variantNum, resourceType, extensionName);
        }

        @Override
        public synchronized Map<String, ExtensionAttributes> getAllExtensionsAttributesByIdentifiableId(UUID networkUuid, int variantNum, ResourceType resourceType, String identifiableId) {
            byIdentifiableIdLoads++;
            return super.getAllExtensionsAttributesByIdentifiableId(networkUuid, variantNum, resourceType, identifiableId);
        }

        @Override
        public synchronized Optional<ExtensionAttributes> getExtensionAttributes(UUID networkUuid, int variantNum, ResourceType resourceType, String identifiableId, String extensionName) {
            singleLoads++;
            return super.getExtensionAttributes(networkUuid, variantNum, resourceType, identifiableId, extensionName);
        }

        private synchronized Map<ResourceType, Integer> getBulkLoadsByType() {
            return new EnumMap<>(bulkLoadsByType);
        }

        private synchronized int getLazyLoads() {
            return byNameLoads + byIdentifiableIdLoads + singleLoads;
        }
    }

    @Test
    public void testExtensionsPreloadedWithComputationStrategy() {
        var offlineClient = new ExtensionCountingClient();
        var client = new PreloadingNetworkStoreClient(new CachedNetworkStoreClient(offlineClient),
            PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_COMPUTATION, ForkJoinPool.commonPool());
        UUID networkUuid = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
        client.getSubstations(networkUuid, 0);

        // the preloading has bulk loaded the extensions of every preloaded collection, exactly once per type
        Map<ResourceType, Integer> bulkLoadsByType = offlineClient.getBulkLoadsByType();
        assertEquals(PreloadingNetworkStoreClient.RESOURCE_TYPES_NEEDED_FOR_COMPUTATION, bulkLoadsByType.keySet());
        bulkLoadsByType.forEach((resourceType, count) -> assertEquals(1, (int) count));
        assertEquals(0, offlineClient.getLazyLoads());

        // any further extension access is a cache hit (even a miss on an absent extension), no delegate call
        assertFalse(client.getExtensionAttributes(networkUuid, 0, ResourceType.GENERATOR, "gen1", "activePowerControl").isPresent());
        assertTrue(client.getAllExtensionsAttributesByIdentifiableId(networkUuid, 0, ResourceType.GENERATOR, "gen1").isEmpty());
        bulkLoadsByType = offlineClient.getBulkLoadsByType();
        assertEquals(PreloadingNetworkStoreClient.RESOURCE_TYPES_NEEDED_FOR_COMPUTATION, bulkLoadsByType.keySet());
        bulkLoadsByType.forEach((resourceType, count) -> assertEquals(1, (int) count));
        assertEquals(0, offlineClient.getLazyLoads());
    }

    @Test
    public void testExtensionsNotPreloadedWithBusViewStrategy() {
        var offlineClient = new ExtensionCountingClient();
        var client = new PreloadingNetworkStoreClient(new CachedNetworkStoreClient(offlineClient),
            PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW, ForkJoinPool.commonPool());
        UUID networkUuid = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
        client.getSubstations(networkUuid, 0);
        assertTrue(offlineClient.getBulkLoadsByType().isEmpty());
        assertEquals(0, offlineClient.getLazyLoads());
    }
}
