package com.swiftdrop.operations;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Kafka infrastructure for operations-service Spring integration tests.
 *
 * <p>The container is started once when this test infrastructure is loaded and
 * is intentionally not managed by the per-class {@code @Testcontainers}
 * extension. Spring may cache application contexts between test classes, so
 * stopping the broker at the end of one class could invalidate a context used
 * by another class. Testcontainers' Ryuk process cleans up the container when
 * the Maven test JVM exits.</p>
 */
public abstract class OperationsKafkaIntegrationTest {

    private static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("apache/kafka:3.8.1")
    );

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void configureKafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
