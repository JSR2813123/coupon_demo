package com.example.coupon_demo.service;

import com.example.coupon_demo.entity.CouponCampaign;
import com.example.coupon_demo.entity.UserCoupon;
import com.example.coupon_demo.repository.CouponCampaignRepository;
import com.example.coupon_demo.repository.UserCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.Collections;



@Service
public class CouponClaimPessimisticService {
    private final CouponCampaignRepository couponCampaignRepository;
    private final UserCouponRepository userCouponRepository;
    private final StringRedisTemplate redisTemplate;
    private static final DefaultRedisScript<Long> DECR_IF_AVAILABLE_SCRIPT;

    static {
        DECR_IF_AVAILABLE_SCRIPT =new DefaultRedisScript<>();
        DECR_IF_AVAILABLE_SCRIPT.setScriptText(
                "local stock = tonumber(redis.call('GET',KEYS[1])) "+
                        "if not stock then return -2 end "+
                        "if stock <= 0 then return -1 end "+
                        "return redis.call('DECR',KEYS[1]) "
        );
        DECR_IF_AVAILABLE_SCRIPT.setResultType(Long.class);
    }

    public CouponClaimPessimisticService(CouponCampaignRepository couponCampaignRepository,
                                        UserCouponRepository userCouponRepository, StringRedisTemplate redisTemplate){
        this.couponCampaignRepository = couponCampaignRepository;
        this.userCouponRepository = userCouponRepository;
        this.redisTemplate = redisTemplate;

    }


    //放在這裡是而不是放在doClaimOnce上面，是因為doClaimOnce的用法我是直接在claim內部呼叫的，然後兩個都在這個class，
    //放在doClaimOnce上有可能會讓@Transactional沒有生效，導致查完狀態後就釋放，沒有建立正確的transaction scope
    //但放在這有Transactional可以在完成整個流程前都確保row lock存在，直到完成才釋放
    @Transactional
    public String claim(Long campaignId, Long userId, String requestId){

        String redisKey = "coupon_stock:"+campaignId;

        Long stock = redisTemplate.execute(
                DECR_IF_AVAILABLE_SCRIPT,
                Collections.singletonList(redisKey)
        );

        if(stock == null){
            return "REDIS_ERROR";
        }

        if(stock == -2L){
            return "REDIS_KEY_NOT_FOUND";
        }

        if(stock == -1L){
            return "Redis_SOLD_OUT";
        }

        try{
            String result = doClaimOnce(campaignId,userId,requestId);
            //非成功情況的redis_key數量補償
            if(!"SUCCESS".equals(result)){
                redisTemplate.opsForValue().increment(redisKey);
            }

            return result;

        }catch(RuntimeException e){
            redisTemplate.opsForValue().increment(redisKey);
            throw e;
        }
    }


    public String doClaimOnce(Long campaignId,Long userId, String requestId){
        //不處理redis的部分，只保留核心帳務邏輯，避免後續除錯或邏輯判斷過於複雜

        //看是否有重複的requestId
        if(userCouponRepository.findByRequestId(requestId).isPresent()){
            return  "DUPLICATE_REQUEST";
        }
        //看同一個使用者在同一個活動是否處理過
        if(userCouponRepository.existsByCampaignIdAndUserId(campaignId,userId)){
            return "ALREADY_CLAIMED";
        }
        //用campaignId確認活動是不是存在
        CouponCampaign campaign =couponCampaignRepository.findByIdForUpdate((campaignId))
                //fail fast，直接讓系統停止。而不是繼續跑出更多錯
                .orElseThrow(()-> new RuntimeException("CAMPAIGN_NOT_FOUND"));

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
