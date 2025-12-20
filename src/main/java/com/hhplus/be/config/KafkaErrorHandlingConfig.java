package com.hhplus.be.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer 및 Error Handling 통합 설정
 *
 * 역할:
 * - ConsumerFactory 설정 (Deserializer, ACK 모드 등)
 * - Error Handler 설정 (DLT, Retry 전략)
 * - Container Factory 설정 (기본, DLT 전용)
 *
 * 설계 개선:
 * - KafkaConsumerConfig와 KafkaErrorHandlingConfig 통합
 * - 빈 이름 충돌 해결
 * - 설정의 명확성 확보
 */
@EnableKafka
@Configuration
public class KafkaErrorHandlingConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * Kafka ConsumerFactory 설정
     *
     * - Bootstrap Servers: Kafka 브로커 주소
     * - Deserializer: String (key), JSON (value)
     * - Auto Commit: false (수동 ACK)
     * - Trusted Packages: 도메인 이벤트 패키지만 허용 (보안)
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // 보안 강화: 도메인 이벤트 패키지만 역직렬화 허용
        // "*" 사용 시 RCE(Remote Code Execution) 리스크
        // 새 이벤트 추가 시 여기에 패키지 추가 필요
        props.put(JsonDeserializer.TRUSTED_PACKAGES,
                "com.hhplus.be.order.domain.event," +
                "com.hhplus.be.coupon.domain.event," +
                "com.hhplus.be.product.domain.event");

        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);  // Producer의 타입 정보 사용

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Kafka Error Handler 설정
     *
     * - 실패 시 재시도: 2회 (1초 간격)
     * - 최종 실패 시: <원본토픽>.DLT로 전송
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
        // 실패 메시지를 <원본토픽>.DLT 로 보냄
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate,
                        (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

        FixedBackOff backOff = new FixedBackOff(1000L, 2);

        // 필요 시 특정 예외는 retry 안 하게도 가능
        // errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        return new DefaultErrorHandler(recoverer, backOff);
    }

    /**
     * 기본 Kafka Listener Container Factory
     *
     * - Error Handler 적용 (재시도 + DLT)
     * - 수동 ACK 모드
     * - 동시성 설정은 각 @KafkaListener에서 개별 지정
     *
     * 동시성 설정 가이드:
     * - concurrency: Consumer 스레드 수
     * - 파티션 수와 동일하거나 적게 설정 (concurrency <= partitions)
     * - 파티션 수보다 많으면 스레드 낭비 + 리밸런싱 빈도 증가
     * - 예: 파티션 3개 → concurrency 3 (각 스레드가 파티션 1개씩 처리)
     * - 예: 파티션 6개 → concurrency 3 or 6 (CPU/DB 부담 고려)
     *
     * 환경별 튜닝:
     * - application.yml에서 Consumer별 concurrency 설정 가능
     * - 각 @KafkaListener가 해당 설정값을 읽어 적용
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);

        // 수동 ACK 모드
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // 동시성 설정은 각 @KafkaListener에서 개별 지정
        // (Consumer별로 다른 concurrency 값을 사용하기 위함)

        // 폴링 설정 (성능 튜닝)
        // - pollTimeout: 브로커에서 메시지 가져올 때 대기 시간
        // - 기본값 1초, 짧게 하면 반응성 향상하지만 CPU 사용 증가
        factory.getContainerProperties().setPollTimeout(1000);

        return factory;
    }

    /**
     * DLT 전용 containerFactory
     *
     * - Error Handler 없음 (DLT에서 예외 발생 시 무한 루프 방지)
     * - 수동 ACK 사용
     * - DLT Consumer에서만 사용
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> dltListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory);

        // AckMode 설정
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        return factory;
    }
}
