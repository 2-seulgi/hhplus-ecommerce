package com.hhplus.be.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer 설정
 *
 * 신뢰성과 성능을 모두 고려한 프로듀서 설정:
 * - 멱등성(Idempotence): 중복 메시지 방지
 * - 재시도(Retries): 일시적 장애 복구
 * - 배치(Batching): 처리량 향상
 * - 압축(Compression): 네트워크 대역폭 절약
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();

        // === 기본 설정 ===
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // === 신뢰성 설정 ===

        // 1. 멱등성 활성화 (중복 메시지 방지)
        // - 프로듀서 ID와 시퀀스 번호를 사용하여 중복 제거
        // - Kafka 0.11+ 기본값이지만 명시적으로 설정
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // 2. ACK 설정 (all = -1)
        // - 모든 ISR(In-Sync Replica)로부터 ACK 받음
        // - 데이터 유실 방지
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        // 3. 재시도 설정
        // - 네트워크 일시 장애, 리더 선출 등 복구 가능한 오류 재시도
        // - Idempotence 활성화 시 자동으로 Integer.MAX_VALUE로 설정되지만 명시
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);

        // 4. In-flight 요청 제한
        // - 동시에 전송 가능한 미확인 요청 수
        // - Idempotence 활성화 시 5 이하로 제한 (순서 보장)
        // - 1로 설정하면 완전한 순서 보장하지만 처리량 감소
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // 5. 전송 타임아웃
        // - 메시지 전송 완료 대기 최대 시간
        // - retries + linger 시간을 고려하여 충분히 설정
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000); // 2분

        // 6. 요청 타임아웃
        // - 브로커 응답 대기 최대 시간
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000); // 30초

        // === 성능 최적화 설정 ===

        // 7. 배치 크기
        // - 같은 파티션으로 가는 메시지를 배치로 묶어 전송
        // - 16KB: 처리량과 지연시간의 균형
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 16KB

        // 8. Linger 시간
        // - 배치가 가득 차지 않아도 전송하기 전 대기 시간
        // - 10ms: 적은 지연으로 배치 효과 극대화
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);

        // 9. 압축
        // - 네트워크 대역폭 절약, 처리량 향상
        // - snappy: 압축률과 속도의 균형 (권장)
        // - gzip: 높은 압축률, 느린 속도
        // - lz4: 빠른 속도, 낮은 압축률
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // 10. 버퍼 메모리
        // - Producer가 사용할 수 있는 최대 메모리
        // - 32MB: 기본값, 처리량이 높으면 증가 고려
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 32MB

        // === JSON 직렬화 설정 ===

        // 타입 정보를 헤더에 포함 (Consumer가 역직렬화할 때 사용)
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}