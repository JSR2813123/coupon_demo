package com.example.coupon_demo.service;

import com.example.coupon_demo.entity.CouponCampaign;
import com.example.coupon_demo.entity.UserCoupon;
import com.example.coupon_demo.repository.CouponCampaignRepository;
import com.example.coupon_demo.repository.UserCouponRepository;
import com.sun.net.httpserver.Authenticator;
import org.apache.catalina.User;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;



@Service
public class CouponClaimService {

    private final CouponCampaignRepository couponCampaignRepository;
    private final UserCouponRepository userCouponRepository;
    private final StringRedisTemplate redisTemplate;

    public CouponClaimService(CouponCampaignRepository couponCampaignRepository,
                              UserCouponRepository userCouponRepository,StringRedisTemplate redisTemplate){
        this.couponCampaignRepository = couponCampaignRepository;
        this.userCouponRepository = userCouponRepository;
        this.redisTemplate = redisTemplate;

    }

    public String claim(Long campaignId, Long userId, String requestId){
        //@先預設為3次，之後用JMeter看最佳結果
        int maxRetry=3;

        for(int RetryTime=1;RetryTime<=maxRetry;RetryTime++){
            try{
                return doClaimOnce(campaignId,userId,requestId);
            }catch (ObjectOptimisticLockingFailureException e){
                Long stock =redisTemplate.opsForValue().increment("coupon_stock:"+campaignId);
                if(RetryTime==maxRetry){
                    return "CONFLICT_TRY_AGAIN1";
                }
            }
        }
        return "CONFLICT_TRY_AGAIN";
    }


    @Transactional
    public String doClaimOnce(Long campaignId,Long userId, String requestId){
        //@出現一個問題，如果redis成功，但是在DB失敗的話，會導致不一致
        //@應該要把redis的扣除回滾或加回失敗的數量

        //減去coupon_stock的數量，配合campaign_id
        Long stock =redisTemplate.opsForValue().decrement("coupon_stock:"+campaignId);

        if(stock==null||stock<0){
            return "redis_SOLD_OUT";
        }
        //看是否有重複的requestId
        if(userCouponRepository.findByRequestId(requestId).isPresent()){
            return  "DUPLICATE_REQUEST";
        }
        //看同一個使用者在同一個活動是否處理過
        if(userCouponRepository.existsByCampaignIdAndUserId(campaignId,userId)){
            return "ALREADY_CLAIMED";
        }
        //用campaignId確認活動是不是存在
        CouponCampaign campaign =couponCampaignRepository.findById((campaignId))
                //fail fast，直接讓系統停止。而不是繼續跑出更多錯
                .orElseThrow(()-> new RuntimeException("CAMPAIGN_NOT_FOUND"));
        try{
            Thread.sleep(100);
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }

        //檢查活動狀態是不是ACTIVE
        if(!"ACTIVE".equals(campaign.getStatus())){
                return  "CAMPAIGN_NOT_ACTIVE";
        }
        //檢查庫存有沒有被領光
        if(campaign.getIssueCount()>=campaign.getTotalLimit()){
            return "SOLD_OUT";
        }
        //增加發行的數量並記錄
        campaign.setIssueCount(campaign.getIssueCount()+1);
        couponCampaignRepository.save(campaign);

        //建立user_coupon紀錄
        UserCoupon userCoupon = UserCoupon.success(campaignId,userId,requestId);
        userCouponRepository.save(userCoupon);

        return "SUCCESS";

    }

}


