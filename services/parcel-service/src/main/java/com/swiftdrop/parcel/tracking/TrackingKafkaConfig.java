package com.swiftdrop.parcel.tracking;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Listener container for the history writer with the team retry policy (topics.md section 2):
 * 5 attempts with 1 s, 2 s, 4 s, 8 s back-off (16 s cap), then the record goes to
 * {@code parcel.status-changed.v1.dlt}. Unusable messages go to the DLT at once.
 */
@Configuration
class TrackingKafkaConfig {

    static final String WRITER_CONTAINER_FACTORY = "trackingWriterContainerFactory";
    static final String DLT_SUFFIX = ".dlt";

    @Bean(WRITER_CONTAINER_FACTORY)
    ConcurrentKafkaListenerContainerFactory<Object, Object> trackingWriterContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaOperations<Object, Object> kafkaOperations) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(writerErrorHandler(kafkaOperations));
        return factory;
    }

    static DefaultErrorHandler writerErrorHandler(KafkaOperations<Object, Object> kafkaOperations) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(4);
        backOff.setInitialInterval(1_000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(16_000);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidTrackingEventException.class);
        return handler;
    }
}
