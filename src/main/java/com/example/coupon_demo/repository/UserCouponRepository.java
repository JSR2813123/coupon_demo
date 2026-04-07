package com.example.coupon_demo.repository;

import com.example.coupon_demo.entity.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long>{
    boolean existsByCampaignIdAndUserId(Long campaignId, Long userId);

    Optional<UserCoupon> findByRequestId(String requestId);
}
