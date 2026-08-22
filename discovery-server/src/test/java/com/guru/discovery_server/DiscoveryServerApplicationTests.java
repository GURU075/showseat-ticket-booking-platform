package com.guru.discovery_server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties = {
                "server.port=0",
                "eureka.client.register-with-eureka=false",
                "eureka.client.fetch-registry=false"
        }
)
class DiscoveryServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
