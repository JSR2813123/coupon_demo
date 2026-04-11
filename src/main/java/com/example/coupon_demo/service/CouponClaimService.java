package com.example.coupon_demo.service;

import com.example.coupon_demo.entity.CouponCampaign;
import com.example.coupon_demo.entity.UserCoupon;
import com.example.coupon_demo.repository.CouponCampaignRepository;
import com.example.coupon_demo.repository.UserCouponRepository;
import com.sun.net.httpserver.Authenticator;
import org.apache.catalina.User;
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

    public String claim(Long campaignId, Long userID, String requestId){
        //@先預設為3次，之後用JMeter看最佳結果
        int maxRetry=3;

        for(int RetryTime=1;RetryTime<=maxRetry;RetryTime++){
            try{
                return doClaimOnce(campaignId,userID,requestId);
            }catch (ObjectOptimisticLockingFailureException e){
                if(RetryTime==maxRetry){
                    return "樂觀鎖衝突，已經自動重試3次，請重新整理";
                }

                try{
                    Thread.sleep(RetryTime*50L);
                }catch(InterruptedException e2){
                    Thread.currentThread().interrupt();
                    return "interrupt";
                }
            }
        }
        return "樂觀鎖衝突，請重試";
    }


    @Transactional
    public String doClaimOnce(Long campaignId,Long userId, String requestId){
        //看是否有重複的requestId
        if(userCouponRepository.findByRequestId(requestId).isPresent()){
            return "重複請求";
        }
        //看同一個使用者在同一個活動是否處理過
        if(userCouponRepository.existsByCampaignIdAndUserId(campaignId,userId)){
            return "同一個使用者和同個活動請求";
        }
        //用campaignId確認活動是不是存在
        CouponCampaign campaign =couponCampaignRepository.findById((campaignId))
                //fail fast，直接讓系統停止。而不是繼續跑出更多錯
                .orElseThrow(()-> new RuntimeException("找不到該活動"));
        //檢查活動狀態是不是ACTIVE
        if(!"ACTIVE".equals(campaign.getStatus())){
                return "活動不是ACTIVE";
        }
        //檢查庫存有沒有被領光
        if(campaign.getIssueCount()>=campaign.getTotalLimit()){
            return "優惠券已經發完了";
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


