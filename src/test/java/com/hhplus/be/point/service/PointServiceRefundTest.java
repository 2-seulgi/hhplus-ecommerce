package com.hhplus.be.point.service;

import com.hhplus.be.point.domain.repository.PointRepository;
import com.hhplus.be.user.domain.model.User;
import com.hhplus.be.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * PointService 환불 테스트
 * <p>
 * 도메인 객체 변경 후 save() 호출 검증
 */
@ExtendWith(MockitoExtension.class)
class PointServiceRefundTest {

    @Mock private UserRepository userRepository;
    @Mock private PointRepository pointRepository;

    @InjectMocks private PointService pointService;

    @Test
    @DisplayName("포인트 환불 시 User save() 호출됨")
    void refundPoints_savesUser() {
        // Given
        Long userId = 1L;
        int refundAmount = 10000;
        int initialBalance = 50000;

        User user = User.create(userId, "테스트유저", "test@test.com", initialBalance);

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = pointService.refundPoints(userId, refundAmount);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getBalance()).isEqualTo(initialBalance + refundAmount);

        // Verify save() 호출 확인
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("포인트 차감 시 User save() 호출됨")
    void deductPoints_savesUser() {
        // Given
        Long userId = 1L;
        int deductAmount = 10000;
        int initialBalance = 50000;

        User user = User.create(userId, "테스트유저", "test@test.com", initialBalance);

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = pointService.deductPoints(userId, deductAmount);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getBalance()).isEqualTo(initialBalance - deductAmount);

        // Verify save() 호출 확인
        verify(userRepository).save(user);
    }
}