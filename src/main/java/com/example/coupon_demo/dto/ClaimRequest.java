package com.example.coupon_demo.dto;

public class ClaimRequest {
    private Long userId;
    private String requestId;

    public Long getUserId(){
        return userId;
    }

    public String getRequestId(){
        return requestId;
    }

    public void setUserId(Long userId){
        this.userId=userId;
    }
    public void setRequestId(String requestId){
        this.requestId=requestId;
    }

}
