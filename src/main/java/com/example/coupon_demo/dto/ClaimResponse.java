package com.example.coupon_demo.dto;

public class ClaimResponse {
    private String result;
    public ClaimResponse(String result){
        this.result=result;
    }
    public String getResult(){
        return result;
    }
}
