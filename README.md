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

---

## 📂 Project Structure
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

---

## 📂 Project Structure
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

---

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

note
目前為基礎版本，尚未處理高併發問題（可能發生超發）。

Next Step
    Optimistic Lock（樂觀鎖）
    Redis 限流
    高併發處理
