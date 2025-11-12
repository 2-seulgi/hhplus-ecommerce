package com.hhplus.be.usercoupon.service;

import com.hhplus.be.common.exception.BusinessException;
import com.hhplus.be.common.exception.ResourceNotFoundException;
import com.hhplus.be.coupon.domain.model.Coupon;
import com.hhplus.be.coupon.domain.repository.CouponRepository;
import com.hhplus.be.user.domain.repository.UserRepository;
import com.hhplus.be.usercoupon.domain.model.UserCoupon;
import com.hhplus.be.usercoupon.domain.repository.UserCouponRepository;
import com.hhplus.be.usercoupon.service.dto.GetUserCouponsQuery;
import com.hhplus.be.usercoupon.service.dto.GetUserCouponsResult;
import com.hhplus.be.usercoupon.service.dto.IssueCouponCommand;
import com.hhplus.be.usercoupon.service.dto.IssueCouponResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class UserCouponService {

    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    // 쿠폰ID별 뮤텍스
    private final ConcurrentHashMap<Long, ReentrantLock> couponLocks = new ConcurrentHashMap<>();

    private ReentrantLock lockOf(Long couponId) {
        return couponLocks.computeIfAbsent(couponId, id -> new ReentrantLock());
    }

    /**
     * 쿠폰 발급
     *
     * 비즈니스 규칙:
     * 1. 사용자 존재 확인
     * 2. 쿠폰 존재 확인
     * 3. 발급 기간 확인 (issueStartAt ~ issueEndAt)
     * 4. 중복 발급 확인 (1인 1회 제한)
     * 5. 발급 수량 확인 (낙관적 락으로 동시성 제어)
     * 6. 쿠폰 발급 수량 증가
     * 7. UserCoupon 생성
     */
    @Transactional
    public IssueCouponResult issueCoupon(IssueCouponCommand command) {
        ReentrantLock lock = lockOf(command.couponId());
        lock.lock();
        try {
            // 1. 사용자 존재 확인
            userRepository.findById(command.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 회원입니다"));

            // 2. 쿠폰 존재 확인
            Coupon coupon = couponRepository.findById(command.couponId())
                    .orElseThrow(() -> new ResourceNotFoundException("쿠폰을 찾을 수 없습니다"));

            // 3. 발급 기간 확인
            Instant now = Instant.now();
            if (now.isBefore(coupon.getIssueStartAt()) || now.isAfter(coupon.getIssueEndAt())) {
                throw new BusinessException("쿠폰 발급 기간이 아닙니다", "ISSUE_PERIOD_EXPIRED");
            }

            // 4. 중복 발급 확인
            userCouponRepository.findByUserIdAndCouponId(command.userId(), command.couponId())
                    .ifPresent(uc -> {
                        throw new BusinessException("이미 발급받은 쿠폰입니다", "ALREADY_ISSUED");
                    });

            // 5-6. 쿠폰 발급 수량 증가 (낙관적 락으로 동시성 제어)
            // Coupon.increaseIssued()는 발급 가능 여부 확인 후 증가
            coupon.increaseIssued();
            couponRepository.save(coupon);  // @Version으로 낙관적 락 적용

            // 7. UserCoupon 생성
            UserCoupon userCoupon = UserCoupon.create(command.userId(), command.couponId(), now);
            userCouponRepository.save(userCoupon);

            return IssueCouponResult.from(userCoupon, coupon);
        }finally {
            lock.unlock();
        }
    }

    /**
     * 보유 쿠폰 조회
     *
     * 비즈니스 규칙:
     * 1. 사용자 존재 확인
     * 2. 사용자의 쿠폰 조회
     * 3. available=true면 사용 가능한 쿠폰만 필터링
     *    - used=false
     *    - 사용 기간 내 (useStartAt ~ useEndAt)
     */
    @Transactional(readOnly = true)
    public GetUserCouponsResult getUserCoupons(GetUserCouponsQuery query) {
        // 1. 사용자 존재 확인
        userRepository.findById(query.userId())
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 회원입니다"));

        // 2. 사용자의 쿠폰 조회
        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(query.userId());

        Instant now = Instant.now();

        // 3. 각 UserCoupon에 대해 Coupon 정보 조합 및 필터링
        List<GetUserCouponsResult.UserCouponInfo> couponInfos = userCoupons.stream()
                .map(uc -> {
                    Coupon coupon = couponRepository.findById(uc.getCouponId())
                            .orElseThrow(() -> new ResourceNotFoundException("쿠폰을 찾을 수 없습니다"));
                    return GetUserCouponsResult.UserCouponInfo.from(uc, coupon);
                })
                .filter(info -> {
                    // available=true면 사용 가능한 쿠폰만
                    if (Boolean.TRUE.equals(query.available())) {
                        return !info.used()
                                && !now.isBefore(info.useStartAt())
                                && !now.isAfter(info.useEndAt());
                    }
                    // available=false 또는 null이면 전체
                    return true;
                })
                .toList();

        return new GetUserCouponsResult(couponInfos);
    }


}