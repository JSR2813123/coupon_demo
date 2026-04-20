# Coupon Demo (Spring Boot)

一個簡單的優惠券領取系統，用來模擬高併發場景下的發券邏輯。

---

## 🚀 Tech Stack

- Java 21
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Maven

---

## 📌 Features

- 領取優惠券 API
- 防重複請求（requestId）
- 防止同一用戶重複領取
- 庫存限制（total_limit）
- 狀態檢查（ACTIVE / SOLD_OUT）

## 📂 Project Structure

controller → API入口
service → 核心邏輯
repository → DB操作
entity → 資料表映射
dto → request / response

## 🔗 API

### Claim Coupon

POST `/api/v1/coupon/{campaignId}/claim`

### Request Body

```json
{
  "userId": 1001,
  "requestId": "req-001"
}

### Response
{
  "result": "SUCCESS"
}


Database

coupon_campaign
    id
    code
    total_limit
    issue_count
    status
    version
    create_at

coupon_campaign
    id
    code
    total_limit
    issue_count
    status
    version
    create_at

已完成功能
PostgreSQL 持久化
防重複請求（requestId）
防重複領取（campaignId + userId）
JPA 樂觀鎖
retry（最多 3 次）
Redis 庫存閘門
Redis Lua 原子扣減

流程概述
先透過 Redis Lua script 判斷是否還有庫存，只有 stock > 0 才扣減
扣減成功後才進入 DB 流程
DB 端再檢查重複請求 / 重複領取 / 活動狀態
使用樂觀鎖避免多個請求同時成功更新同一筆活動資料
若 DB 最終失敗，補回 Redis 庫存
增加不同retry的thread.sleep時間的併發導致失敗實驗比較(word,excel)
實驗結論為用Thread.sleep(retryTime*50L)會比baseDelay + retryTime * stepDelay + jitter的併發數多約30%，且在併發樣本高才顯現，而jitter方式的latency可能高於純粹線性的thread.sleep，但亦可能為jitter本身的baseDelay和stepDelay設定變數就比thread.sleep()高導致。

測試方式
Docker 啟動 Redis
JMeter 併發請求

已知限制
retry 與 transaction 結構仍可再重構
結果碼仍是字串，後續可改 enum
尚未做完整監控與指標統計








