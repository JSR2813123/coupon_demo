package com.example.coupon_demo.controller;

import com.example.coupon_demo.dto.ClaimResponse;
import com.example.coupon_demo.dto.ClaimRequest;
import com.example.coupon_demo.service.CouponClaimService;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/coupon")
public class CouponController {

    private final CouponClaimService couponClaimService;

    public CouponController(CouponClaimService couponClaimService){
        this.couponClaimService = couponClaimService;
    }

    @PostMapping("/{campaignId}/claim")
    public ClaimResponse claim(@PathVariable Long campaignId,
                               @RequestBody ClaimRequest request){
        String result=couponClaimService.claim(
                campaignId,
                request.getUserId(),
                request.getRequestId()
        );

        return new ClaimResponse(result);

    }

}
