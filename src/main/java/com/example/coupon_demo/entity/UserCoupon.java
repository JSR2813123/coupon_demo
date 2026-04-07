package com.example.coupon_demo.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name="user_coupon",
        uniqueConstraints={
                @UniqueConstraint(name = "uq_campaign_user",columnNames ={"campaign_id", "user_id"}),
                @UniqueConstraint(name = "uq_request_id", columnNames = {"request_id"})

        }
)
public class UserCoupon {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name="campaign_id", nullable=false)
        private Long campaignId;

        @Column(name="user_id",nullable=false)
        private Long userId;

        @Column(name="request_id", nullable = false)
        private String requestId;

        @Column(nullable = false)
        private String status;

        @Column(name="create_at", insertable = false,updatable = false)
        private LocalDateTime createAt;

        public static UserCoupon success(Long campaignId, Long user_id, String requestId){
                UserCoupon uc =new UserCoupon();
                uc.campaignId=campaignId;
                uc.userId=user_id;
                uc.requestId=requestId;
                uc.status="SUCCESS";
                return uc;

        }

        public Long getCampaignId() {return campaignId;}
        public Long getUserId() {return userId;}
        public String getRequestId() {return requestId;}

}
