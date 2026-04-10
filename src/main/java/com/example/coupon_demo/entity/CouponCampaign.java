package com.example.coupon_demo.entity;


import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_campaign")
public class CouponCampaign {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique= true)
    private String code;

    @Column(name="total_limit", nullable = false)
    private Integer totalLimit;

    @Column(name ="issue_count",nullable = false)
    private Integer issueCount;

    @Column(nullable = false)
    private String status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "create_at",insertable=false,updatable=false)
    private LocalDateTime createAt;

    public long getId() {return id;}
    public String getCode() {return code;}
    public Integer getTotalLimit() {return totalLimit;}
    public Integer getIssueCount() {return issueCount;}
    public String getStatus() {return status;}
    public long getVersion() {return  version;}

    public void setIssueCount(Integer issueCount) {
        this.issueCount = issueCount;
    }




}
