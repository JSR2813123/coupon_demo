package com.example.coupon_demo.repository;

import com.example.coupon_demo.entity.CouponCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

//讓悲觀鎖在查詢的時候就可以鎖
//SELECT * FROM coupon_campaign
//WHERE id=?
//FOR UPDATE
import jakarta.persistence.LockModeType;   //鎖的種類
import org.springframework.data.jpa.repository.Lock;  //把鎖套用到查詢方法
import org.springframework.data.jpa.repository.Query;  //自己寫查詢 (JPA Query Language)
import org.springframework.data.repository.query.Param; //把Java參數綁到 query 裡
import java.util.Optional;


public interface CouponCampaignRepository extends JpaRepository<CouponCampaign, Long> {
    //找指定的CouponCampaign
    @Query("SELECT c FROM CouponCampaign c WHERE c.id = :id")
    //找到候用悲觀鎖鎖住對應的這個campaign.id
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CouponCampaign> findByIdForUpdate(@Param("id") Long id);
};



