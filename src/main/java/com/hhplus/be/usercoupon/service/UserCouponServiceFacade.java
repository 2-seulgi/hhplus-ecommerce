package com.hhplus.be.usercoupon.service;

import com.hhplus.be.usercoupon.service.dto.IssueCouponCommand;
import com.hhplus.be.usercoupon.service.dto.IssueCouponResult;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * UserCouponService의 Facade
 *
 * @Retryable과 @Transactional의 AOP 순서 문제를 해결하기 위해
 * retry 로직을 별도 클래스로 분리
 */
@Component
@RequiredArgsConstructor
public class UserCouponServiceFacade {

    private final UserCouponService userCouponService;

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 50,
            backoff = @Backoff(delay = 10, maxDelay = 200)
    )
    public IssueCouponResult issueCoupon(IssueCouponCommand command) {
        return userCouponService.issueCoupon(command);
    }
}
