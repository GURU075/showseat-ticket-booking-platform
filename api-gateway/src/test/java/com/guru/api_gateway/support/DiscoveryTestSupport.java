package com.guru.api_gateway.support;

import com.sun.net.httpserver.HttpServer;
import org.springframework.test.context.DynamicPropertyRegistry;

public final class DiscoveryTestSupport {

    private DiscoveryTestSupport() {
    }

    public static void disableEureka(DynamicPropertyRegistry registry) {
        registry.add("eureka.client.enabled", () -> false);
    }

    public static void registerInstance(
            DynamicPropertyRegistry registry,
            String serviceId,
            int index,
            HttpServer server
    ) {
        registerInstance(
                registry,
                serviceId,
                index,
                "http://127.0.0.1:" + server.getAddress().getPort()
        );
    }

    public static void registerInstance(
            DynamicPropertyRegistry registry,
            String serviceId,
            int index,
            String uri
    ) {
        registry.add(
                "spring.cloud.discovery.client.simple.instances."
                        + serviceId + "[" + index + "].uri",
                () -> uri
        );
    }
}
