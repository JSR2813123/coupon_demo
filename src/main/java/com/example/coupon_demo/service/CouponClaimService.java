package com.example.coupon_demo.service;

import com.example.coupon_demo.entity.CouponCampaign;
import com.example.coupon_demo.entity.UserCoupon;
import com.example.coupon_demo.repository.CouponCampaignRepository;
import com.example.coupon_demo.repository.UserCouponRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class CouponClaimService {

    private final CouponCampaignRepository couponCampaignRepository;
    private final UserCouponRepository userCouponRepository;

    public CouponClaimService(CouponCampaignRepository couponCampaignRepository,
                                 UserCouponRepository userCouponRepository){
        this.couponCampaignRepository = couponCampaignRepository;
        this.userCouponRepository = userCouponRepository;

    }

    @Transactional
    public String claim(Long campaignId, Long userId, String requestId) {
        //看是否有重複的requestId
        if (userCouponRepository.findByRequestId(requestId).isPresent()) {
            return "DUPLICATE_REQUEST";
        }
        //看同一個使用者在同一個活動是否處理過
        if (userCouponRepository.existsByCampaignIdAndUserId(campaignId, userId)) {
            return "ALREADY_CLAIMED";
        }

        try {
            //用campaignId確認活動是不是存在
            CouponCampaign campaign = couponCampaignRepository.findById(campaignId)
                    .orElseThrow(() -> new RuntimeException("Campaign not found"));
            //檢查活動狀態是不是ACTIVE
            if (!"ACTIVE".equals(campaign.getStatus())) {
                return "CAMPAIGN_NOT_ACTIVE";
            }
            //檢查庫存有沒有被領光
            //@但目前這版如果出現同時兩個user都發出請求，然後都成功，會造成有超發的情況發生
            if (campaign.getIssueCount() >= campaign.getTotalLimit()) {
                return "SOLD_OUT";
            }
            //增加發行的數量
            campaign.setIssueCount(campaign.getIssueCount() + 1);
            couponCampaignRepository.save(campaign);
            //建立user_coupon紀錄
            UserCoupon userCoupon = UserCoupon.success(campaignId, userId, requestId);
            userCouponRepository.save(userCoupon);

            return "SUCCESS";
        }catch (ObjectOptimisticLockingFailureException e){
            return "樂觀鎖版本衝突，請重試";
        }
    }

}


