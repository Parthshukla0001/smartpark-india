package com.smartpark.observability;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Health indicator for Apache Kafka.
 *
 * Dependency Classification: NON-CRITICAL (DEGRADED / OUTBOX_BUFFERED).
 * SmartPark utilizes the Transactional Outbox pattern where all domain events
 * are committed transactionally to PostgreSQL before publication to Kafka.
 * If Kafka brokers become unreachable:
 * - REST booking APIs remain fully operational.
 * - Outbox publisher safely retains pending events with retry tracking.
 * Therefore, Kafka broker unavailability reports DEGRADED status without causing
 * the entire application health check to report DOWN.
 */
@Component("kafka")
public class SmartParkKafkaHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(SmartParkKafkaHealthIndicator.class);
    private final KafkaAdmin kafkaAdmin;

    @Autowired
    public SmartParkKafkaHealthIndicator(@Autowired(required = false) KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        if (kafkaAdmin == null) {
            return Health.up()
                    .withDetail("status", "DISABLED")
                    .withDetail("mode", "OUTBOX_BUFFERED")
                    .withDetail("message", "KafkaAdmin not configured")
                    .build();
        }

        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterResult cluster = adminClient.describeCluster();
            String clusterId = cluster.clusterId().get(2, TimeUnit.SECONDS);
            int nodeCount = cluster.nodes().get(2, TimeUnit.SECONDS).size();
            return Health.up()
                    .withDetail("status", "AVAILABLE")
                    .withDetail("clusterId", clusterId)
                    .withDetail("nodes", nodeCount)
                    .build();
        } catch (Exception e) {
            log.warn("Kafka health check failed (non-critical, outbox buffer active): {}", e.getMessage());
            return Health.up()
                    .withDetail("status", "DEGRADED")
                    .withDetail("mode", "OUTBOX_BUFFERED")
                    .withDetail("message", "Kafka broker unreachable; domain events safely buffered in PostgreSQL outbox table")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
