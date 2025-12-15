package com.hhplus.be.usercoupon.service;

import com.hhplus.be.common.annotation.DistributedLock;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserCouponService {

    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final Clock clock;

    /**
     * 쿠폰 발급
     *
     * 트랜잭션 최적화:
     * - 검증 로직: 트랜잭션 외부 (validateCouponIssue)
     * - 업데이트 로직: 트랜잭션 내부 (issueInTransaction)
     * - AopForTransaction에서 REQUIRES_NEW 트랜잭션 관리
     */
    @DistributedLock(key = "'coupon:' + #command.couponId", waitTime = 2, leaseTime = 10)
    public IssueCouponResult issueCoupon(IssueCouponCommand command) {
        Instant now = clock.instant();

        // 1. 검증 (트랜잭션 외부)
        Coupon coupon = validateCouponIssue(command, now);

        // 2. 트랜잭션 처리
        return issueInTransaction(command, coupon, now);
    }

    /**
     * 쿠폰 발급 검증 (트랜잭션 불필요)
     *
     * @return 검증된 Coupon 객체
     * @throws ResourceNotFoundException 사용자/쿠폰을 찾을 수 없는 경우
     * @throws BusinessException 발급 기간이 아니거나 이미 발급받은 경우
     */
    private Coupon validateCouponIssue(IssueCouponCommand command, Instant now) {
        userRepository.findById(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 회원입니다"));
        Coupon coupon = couponRepository.findById(command.couponId())
                .orElseThrow(() -> new ResourceNotFoundException("쿠폰을 찾을 수 없습니다"));
        if (now.isBefore(coupon.getIssueStartAt()) || now.isAfter(coupon.getIssueEndAt())) {
            throw new BusinessException("쿠폰 발급 기간이 아닙니다", "ISSUE_PERIOD_EXPIRED");
        }
        userCouponRepository.findByUserIdAndCouponId(command.userId(), command.couponId())
                .ifPresent(uc -> {
                    throw new BusinessException("이미 발급받은 쿠폰입니다", "ALREADY_ISSUED");
                });
        return coupon;
    }

    /**
     * 쿠폰 발급 트랜잭션 처리
     *
     * AopForTransaction에서 이미 REQUIRES_NEW 트랜잭션을 시작하므로
     * 별도 @Transactional 불필요
     */
    private IssueCouponResult issueInTransaction(IssueCouponCommand command, Coupon coupon, Instant now) {
        coupon.increaseIssued();
        couponRepository.save(coupon);

        UserCoupon userCoupon = UserCoupon.create(command.userId(), command.couponId(), now);
        try {
            userCouponRepository.save(userCoupon);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("이미 발급받은 쿠폰입니다", "ALREADY_ISSUED");
        }

        return IssueCouponResult.from(userCoupon, coupon);
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

        Instant now = clock.instant();

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