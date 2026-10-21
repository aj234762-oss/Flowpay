package com.cfs.FlowPay.kafka;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "app.kafka-enabled",havingValue = "true")
public class KafkaConfig {

    public static final String TOPIC ="flowpay.events";

    // Making kafka Topic
    NewTopic paymentsEvents()
    {
        return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
    }
    // Handling Error while making kafka Topic
    @Bean
    DefaultErrorHandler kafkaErrorHandler()
    {
        return new DefaultErrorHandler(new FixedBackOff(2000,FixedBackOff.UNLIMITED_ATTEMPTS));
    }

}
