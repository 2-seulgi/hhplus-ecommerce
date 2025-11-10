package com.hhplus.be.point.service;

import com.hhplus.be.point.service.dto.*;
import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.point.domain.Point;
import com.hhplus.be.point.domain.PointType;
import com.hhplus.be.user.domain.User;
import com.hhplus.be.point.infrastructure.PointRepository;
import com.hhplus.be.user.infrastructure.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PointRepository pointRepository;

    @InjectMocks
    private PointService pointService;

    @Test
    @DisplayName("포인트 충전 성공 - 잔액과 히스토리 기록이 모두 반영된다")
    void chargePoint() {
        // given
        User user = User.createWithId(1L, "홍길동", "hong@example.com", 10000);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // when
        PointChargeCommand command = new PointChargeCommand(1L, 5000);
        PointChargeResult result = pointService.charge(command);

        // then: 잔액 검증
        assertThat(result.balanceAfter()).isEqualTo(15_000);
        assertThat(user.getBalance()).isEqualTo(15_000);

        // then: 결과 DTO 검증
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.amount()).isEqualTo(5000);
        assertThat(result.pointType()).isEqualTo(PointType.CHARGE);
        assertThat(result.createdAt()).isNotNull();

        // then: 호출 검증
        verify(userRepository).findById(1L);
        verify(userRepository).save(user);

        ArgumentCaptor<Point> pointCaptor = ArgumentCaptor.forClass(Point.class);
        verify(pointRepository).save(pointCaptor.capture());
        Point saved = pointCaptor.getValue();

        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getAmount()).isEqualTo(5000);
        assertThat(saved.getPointType()).isEqualTo(PointType.CHARGE);
        assertThat(saved.getBalanceAfter()).isEqualTo(15000);

        verifyNoMoreInteractions(userRepository, pointRepository);
    }

    @Test
    @DisplayName("포인트 충전 실패 - 존재하지 않는 사용자")
    void chargePointFailWhenUserNotFound() {
        // given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        PointChargeCommand command = new PointChargeCommand(999L, 5000);
        assertThatThrownBy(() -> pointService.charge(command))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("포인트 내역 조회 성공 - 최신순으로 반환된다")
    void getPointHistory() {
        // given
        Long userId = 1L;
        Point point1 = Point.charge(userId, 10000, 20000);
        Point point2 = Point.use(userId, 5000, 15000);
        Point point3 = Point.charge(userId, 3000, 18000);
        List<Point> expectedHistory = List.of(point3, point2, point1);

        when(pointRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(expectedHistory);

        // when
        PointHistoryQuery query = new PointHistoryQuery(userId);
        List<PointHistoryResult> history = pointService.getHistory(query);

        // then
        assertThat(history).hasSize(3);
        assertThat(history.get(0).pointType()).isEqualTo(PointType.CHARGE);
        assertThat(history.get(0).amount()).isEqualTo(3000);
        assertThat(history.get(1).pointType()).isEqualTo(PointType.USE);
        assertThat(history.get(1).amount()).isEqualTo(5000);
        assertThat(history.get(2).pointType()).isEqualTo(PointType.CHARGE);
        assertThat(history.get(2).amount()).isEqualTo(10000);

        verify(pointRepository).findByUserIdOrderByCreatedAtDesc(userId);
        verifyNoMoreInteractions(pointRepository);
    }

    @Test
    @DisplayName("포인트 내역 조회 - 내역이 없으면 빈 리스트 반환")
    void getPointHistoryEmpty() {
        // given
        Long userId = 999L;
        when(pointRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of());

        // when
        PointHistoryQuery query = new PointHistoryQuery(userId);
        List<PointHistoryResult> history = pointService.getHistory(query);

        // then
        assertThat(history).isEmpty();
        verify(pointRepository).findByUserIdOrderByCreatedAtDesc(userId);
        verifyNoMoreInteractions(pointRepository);
    }

    @Test
    @DisplayName("포인트 잔액 조회 성공")
    void getBalance() {
        // given
        User user = User.createWithId(1L, "홍길동", "hong@example.com", 50000);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // when
        PointBalanceQuery query = new PointBalanceQuery(1L);
        PointBalanceResult result = pointService.getBalance(query);

        // then
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.balance()).isEqualTo(50000);

        verify(userRepository).findById(1L);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("포인트 잔액 조회 실패 - 존재하지 않는 사용자")
    void getBalanceFailWhenUserNotFound() {
        // given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        PointBalanceQuery query = new PointBalanceQuery(999L);
        assertThatThrownBy(() -> pointService.getBalance(query))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }
}
