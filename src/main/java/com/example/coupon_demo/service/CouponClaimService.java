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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.Collections;



@Service
public class CouponClaimService {

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
    public CouponClaimService(CouponCampaignRepository couponCampaignRepository,
                              UserCouponRepository userCouponRepository,StringRedisTemplate redisTemplate){
        this.couponCampaignRepository = couponCampaignRepository;
        this.userCouponRepository = userCouponRepository;
        this.redisTemplate = redisTemplate;

    }

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
            return "REDIS_SOLD_OUT";
        }


        //@先預設為3次，之後用JMeter看最佳結果
        int maxRetry=3;

        for(int retryTime=1;retryTime<=maxRetry;retryTime++){
            try{
                String result = doClaimOnce(campaignId,userId,requestId);
                //非成功情況的redis_key數量補償
                if(!"SUCCESS".equals(result)){
                    redisTemplate.opsForValue().increment(redisKey);
                }

                return result;

            }catch (ObjectOptimisticLockingFailureException e){
                if(retryTime==maxRetry){
                    //衝突的redis_key數量補償
                    redisTemplate.opsForValue().increment(redisKey);
                    return "CONFLICT_TRY_AGAIN";
                }

                try{
                    Thread.sleep(retryTime*50L);//
                }catch(InterruptedException e2){
                    Thread.currentThread().interrupt();
                    redisTemplate.opsForValue().increment(redisKey);
                    return "INTERRUPTED";
                }
            }catch(RuntimeException e){
                redisTemplate.opsForValue().increment(redisKey);
                throw e;
            }
        }
        return "CONFLICT_TRY_AGAIN";
    }


    @Transactional
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


