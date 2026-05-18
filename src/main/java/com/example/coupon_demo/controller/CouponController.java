package com.example.coupon_demo.controller;

import com.example.coupon_demo.dto.ClaimResponse;
import com.example.coupon_demo.dto.ClaimRequest;
import com.example.coupon_demo.service.CouponClaimOptimisticService;
import com.example.coupon_demo.service.CouponClaimPessimisticService;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/coupon")
public class CouponController {

    private final CouponClaimOptimisticService optimisticService;
    private final CouponClaimPessimisticService pessimisticService;


    public CouponController(CouponClaimOptimisticService optimisticService,
                            CouponClaimPessimisticService pessimisticService){
        this.optimisticService = optimisticService;
        this.pessimisticService = pessimisticService;
    }

    @PostMapping("/{campaignId}/claim/optimistic")
    public ClaimResponse claim(@PathVariable Long campaignId,
                               @RequestBody ClaimRequest request){
        String result=optimisticService.claim(
                campaignId,
                request.getUserId(),
                request.getRequestId()
        );

        return new ClaimResponse(result);

    }
    @PostMapping("/{campaignId}/claim/pessimistic")
    public ClaimResponse claimPessimistic(@PathVariable Long campaignId,
                                          @RequestBody ClaimRequest request) {
        String result = pessimisticService.claim(
                campaignId,
                request.getUserId(),
                request.getRequestId()
        );

        return new ClaimResponse(result);
    }

}
