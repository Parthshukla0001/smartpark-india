package com.smartpark.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Kafka configuration for asynchronous event streaming.
 * Configures producer/consumer factories, KafkaTemplate, Kafka topics,
 * DLT recoverer, fixed-backoff error handling, and listener container factories.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${smartpark.kafka.topics.booking-events:smartpark.booking.events}")
    private String bookingEventsTopic;

    @Value("${smartpark.kafka.topics.booking-events-dlt:smartpark.booking.events.DLT}")
    private String bookingEventsDltTopic;

    @Value("${smartpark.kafka.consumer-group:smartpark-notification-group}")
    private String consumerGroup;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public NewTopic bookingEventsTopic() {
        return TopicBuilder.name(bookingEventsTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic bookingEventsDltTopic() {
        return TopicBuilder.name(bookingEventsDltTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<String, String> template) {
        return new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(bookingEventsDltTopic, record.partition()));
    }

    @Bean
    public DefaultErrorHandler errorHandler(DeadLetterPublishingRecoverer recoverer) {
        // 2 retries with 1000ms delay between attempts before recovering to DLT
        FixedBackOff backOff = new FixedBackOff(1000L, 2L);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("Kafka consumer failed delivery attempt {} for topic {} partition {} offset {}: {}",
                    deliveryAttempt, record.topic(), record.partition(), record.offset(), ex.getMessage());
        });
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler errorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
