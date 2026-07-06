/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.powsybl.iidm.network.Country;
import com.powsybl.network.store.iidm.impl.CachedNetworkStoreClient;
import com.powsybl.network.store.model.AllCollectionsBundle;
import com.powsybl.network.store.model.IdentifiableAttributes;
import com.powsybl.network.store.model.LimitsAttributes;
import com.powsybl.network.store.model.LineAttributes;
import com.powsybl.network.store.model.OperationalLimitsGroupAttributes;
import com.powsybl.network.store.model.Resource;
import com.powsybl.network.store.model.ResourceType;
import com.powsybl.network.store.model.SubstationAttributes;
import com.powsybl.network.store.model.TopLevelDocument;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifies the single round trip loading of all the collections by the computation preloading
 * strategy, and the fallback to per collection loading when the server does not expose the
 * collections endpoint.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@RunWith(SpringRunner.class)
@RestClientTest
public class AllCollectionsBundleLoadingTest {

    @SpringBootConfiguration
    static class Config {
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public RestClient testClient(RestTemplateBuilder restTemplateBuilder) {
            return new RestClientImpl(restTemplateBuilder);
        }
    }

    @Autowired
    private RestClient restClient;

    @Autowired
    private MockRestServiceServer server;

    @Autowired
    private ObjectMapper objectMapper;

    private PreloadingNetworkStoreClient client;
    private UUID networkUuid;

    @Before
    public void setUp() {
        RestNetworkStoreClient restStoreClient = new RestNetworkStoreClient(restClient);
        client = new PreloadingNetworkStoreClient(new CachedNetworkStoreClient(new BufferedNetworkStoreClient(restStoreClient, ForkJoinPool.commonPool())),
            PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_COMPUTATION, ForkJoinPool.commonPool());
        networkUuid = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    }

    private static Resource<SubstationAttributes> createSubstation() {
        return Resource.substationBuilder()
            .id("sub1")
            .attributes(SubstationAttributes.builder()
                .country(Country.FR)
                .name("sub1")
                .build())
            .build();
    }

    private static Resource<LineAttributes> createLine() {
        return Resource.lineBuilder()
            .id("line1")
            .attributes(LineAttributes.builder()
                .voltageLevelId1("vl1")
                .voltageLevelId2("vl2")
                .selectedOperationalLimitsGroupId1("group1")
                .build())
            .build();
    }

    @Test
    public void testSingleRoundTripLoading() throws Exception {
        Map<ResourceType, List<Resource<IdentifiableAttributes>>> resources = new EnumMap<>(ResourceType.class);
        resources.put(ResourceType.SUBSTATION, List.of((Resource<IdentifiableAttributes>) (Resource<?>) createSubstation()));
        resources.put(ResourceType.LINE, List.of((Resource<IdentifiableAttributes>) (Resource<?>) createLine()));
        Map<ResourceType, Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>>> limits = new EnumMap<>(ResourceType.class);
        limits.put(ResourceType.LINE, Map.of("line1", Map.of(1, Map.of("group1", OperationalLimitsGroupAttributes.builder()
            .id("group1")
            .currentLimits(LimitsAttributes.builder().permanentLimit(1000).build())
            .build()))));
        AllCollectionsBundle bundle = new AllCollectionsBundle(resources, limits);

        // one single request expected, no per collection request
        server.expect(ExpectedCount.once(), requestTo("/networks/" + networkUuid + "/" + Resource.INITIAL_VARIANT_NUM + "/collections"))
            .andExpect(method(GET))
            .andRespond(withSuccess(objectMapper.writeValueAsString(bundle), MediaType.APPLICATION_JSON));

        // any collection access triggers the single round trip and fills all the caches
        List<Resource<SubstationAttributes>> substations = client.getSubstations(networkUuid, Resource.INITIAL_VARIANT_NUM);
        assertEquals(1, substations.size());
        List<Resource<LineAttributes>> lines = client.getLines(networkUuid, Resource.INITIAL_VARIANT_NUM);
        assertEquals(1, lines.size());
        // the selected operational limits groups are seeded too: no further request
        assertTrue(client.getSelectedOperationalLimitsGroupAttributes(networkUuid, Resource.INITIAL_VARIANT_NUM,
            ResourceType.LINE, "line1", "group1", 1).isPresent());
        server.verify();
    }

    @Test
    public void testFallbackToPerCollectionLoadingWhenEndpointNotAvailable() throws Exception {
        server.expect(ExpectedCount.once(), requestTo("/networks/" + networkUuid + "/" + Resource.INITIAL_VARIANT_NUM + "/collections"))
            .andExpect(method(GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));
        // then one request per collection of the computation set (19), plus the selected
        // operational limits groups of the two branchy types; the requests are parallel so their
        // order is not deterministic: one expectation answers them all, by url
        String substationsJson = objectMapper.writeValueAsString(TopLevelDocument.of(ImmutableList.of(createSubstation())));
        String emptyJson = objectMapper.writeValueAsString(TopLevelDocument.empty());
        server.expect(ExpectedCount.times(21), method(GET))
            .andRespond(request -> {
                String url = request.getURI().toString();
                if (url.contains("/operationalLimitsGroup/selected")) {
                    return withSuccess("{}", MediaType.APPLICATION_JSON).createResponse(request);
                }
                if (url.endsWith("/substations")) {
                    return withSuccess(substationsJson, MediaType.APPLICATION_JSON).createResponse(request);
                }
                return withSuccess(emptyJson, MediaType.APPLICATION_JSON).createResponse(request);
            });

        List<Resource<SubstationAttributes>> substations = client.getSubstations(networkUuid, Resource.INITIAL_VARIANT_NUM);
        assertEquals(1, substations.size());
        server.verify();
    }
}
