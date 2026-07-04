/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.client;

import org.junit.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.junit.Assert.assertFalse;

/**
 * The standalone rest template must not use the JDK HttpClient request factory: it relies on the ForkJoinPool
 * common pool, and a caller running computations on that pool (like open-loadflow multi-thread security analysis)
 * while blocking on REST calls can starve it and deadlock.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class RestClientImplRequestFactoryTest {

    @Test
    public void standaloneRequestFactoryMustNotDependOnTheCommonPool() {
        RestTemplate restTemplate = RestClientImpl.createRestTemplateBuilder("http://localhost:1234/").build();
        assertFalse(restTemplate.getRequestFactory() instanceof JdkClientHttpRequestFactory);
    }
}
