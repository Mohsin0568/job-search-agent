package com.systa;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * A real MongoDB in Docker for tests that need one. Import it into a test and Spring points
 * the Mongo connection at the container; tests sharing a context share the container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MongoTestContainer {

    // Same major version as the local development database. (mongo:8.0 refuses to start on Docker
    // Desktop's 6.19+ Linux kernels - SERVER-121912.)
    @Bean
    @ServiceConnection
    MongoDBContainer mongoDbContainer() {
        return new MongoDBContainer("mongo:7.0");
    }
}
