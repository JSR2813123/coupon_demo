## Table of Contents

1. Project Overview
2. System Architecture
3. Core Features
4. Tech Stack
5. Database Design
6. Claim Flow
7. Concurrency Control Strategy
8. Benchmark & Experiments
9. Known Issues
10. Future Work
11. How to Run



# 1. Project Overview
Coupon Demo（Spring Boot）

本專案是一個優惠券領取系統，用來模擬高併發情境下的發券流程，研究在大量請求同時進入時，如何避免超發、降低資料庫壓力，並比較不同併發控制策略的效果。

本專案主要內容有：

Optimistic Locking / Pessimistic Locking
Retry / Backoff strategies
Redis stock protection
High-concurrency request handling
Latency、Throughput、Conflict analysis
Tomcat Thread Pool / DB Connection Pool 對系統穩定性的影響

# 2. System Architecture
![architecture](docs/architecture.png)

Request flow:

1.Client sends claim request
2.Redis Lua script checks stock atomically
3.Valid requests enter DB transaction
4.Optimistic locking prevents overselling
5.Failed DB transactions restore Redis stock

# 3. Core Features

- Coupon claim API
- Idempotency protection (requestId)
- Duplicate claim prevention
- Redis stock gate
- Redis Lua atomic decrement
- Optimistic locking
- Pessimistic Locking
- Retry mechanism
- Backoff / Jitter strategy
- Conflict handling
- JMeter concurrency benchmark

# 4. Tech Stack

- Java 21
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Redis
- Docker
- Apache JMeter
- Maven

# 5. Database Design

coupon_campaign
Column	                         Description
id	                               活動 ID
code	                          優惠券活動代碼
total_limit	                      發券總上限
issue_count 	                    已發放數量
status	                          活動狀態，例如 ACTIVE / SOLD_OUT
version	                      Optimistic Locking的版本
create_at	                         建立時間

user_coupon
Column	                        Description
id	                             領券紀錄 ID
campaign_id	                  對應 coupon_campaign的id
user_id	                           使用者ID
request_id	                 request的唯一識別(Idempotency)
status	                           領取狀態
create_at	                         建立時間

# 6. Claim Flow

1.Request enters API
2.Redis Lua script checks stock
3.If stock <= 0 → reject
4.DB transaction starts
5.Check duplicate request
6.Check duplicate user claim
7.Check campaign status
8.Update campaign issue_count
9.Insert user_coupon
10.Commit transaction
11.If failed → restore Redis stock


# 7. Concurrency Control Strategy

Redis 
Redis 作為第一層庫存閘門，用來避免大量已經超過庫存的請求直接進入資料庫。
透過 Redis Lua script，可以在 Redis 端原子性完成：

檢查庫存是否存在
判斷 stock 是否大於 0
扣減庫存

這樣可以降低 DB contention，避免所有請求都直接打進 PostgreSQL。

Optimistic Locking
Optimistic Locking 使用 JPA @Version 控制同一筆 coupon_campaign 的併發更新。
當多個請求同時嘗試更新同一筆活動資料時，只有其中一個請求可以成功提交，其他請求會因為 version 不一致而失敗，並進入 retry 流程。

適合情境：
衝突比例可控
不希望請求長時間等待鎖
希望系統在高併發下保持較好的吞吐量

Pessimistic Locking
Pessimistic Locking 透過 DB row lock 控制同一筆 coupon_campaign 的更新順序。
當一個 transaction 鎖住資料列時，其他請求必須等待鎖釋放後才能繼續處理。

適合情境：
資料一致性要求高
衝突頻率很高
希望用序列化的方式降低 retry conflict

但缺點是高併發下可能造成：
latency 上升
transaction 等待時間增加
DB connection 被長時間佔用
timeout / deadlock 風險增加

Retry & Backoff Strategy
Optimistic Locking 發生 conflict 時，系統會進行最多 3 次 retry。
本專案測試不同 retry sleep / backoff / jitter 策略，觀察其對：conflict/request ratio、latency、PR99 latency、success count的影響。



# 8. Benchmark & Experiments

不同retry的thread.sleep時間的併發導致失敗實驗比較(詳細說明都在word,實驗數據都在excel)
實驗目標：
比較不同 retry sleep 策略在高併發情境下，對成功數、conflict 和 latency 的影響。

實驗結論：
使用 Thread.sleep(retryTime * 50L)會比 baseDelay + retryTime * stepDelay + jitter 的併發成功數多約 40%，且在併發樣本較高時才明顯顯現。
jitter 方式的 latency 可能高於純線性的 Thread.sleep()，但也可能是因為 jitter 本身的 baseDelay 和 stepDelay 設定較高所導致。因此可針對不同 request 預期數量調整參數，以降低 latency。


樂觀鎖_測試不同量級請求的效能比較(詳細說明都在word,實驗數據都在excel)
實驗目標：
觀察不同 request scale 下，Optimistic Locking、Redis gate 和本機服務承載能力的表現。

實驗結論：
在不同量級請求下，conflict/request 比例平均約為 6%～7%。Redis 阻擋超發流量的平均值約分布在 92%～98%。其中出現 92% 這種較低值的測試，通常伴隨 Error rate 不為 0，且在 result.jtl 中出現 HttpHostConnectException: Connection refused，以及狀態碼為 NOT_FOUND 的情況。

推測可能原因有三個：
Spring Boot 在測試中直接崩潰
Tomcat thread pool 或 DB connection pool 承受不了瞬間連線
JMeter 2000 threads 瞬間打 localhost，超過本機可承受能力

由此衍生後續研究
1.不同Rame-up情況下的服務連接情況
2.不同backoff+jitter參數的latency、conflict比較
3.悲觀鎖和樂觀鎖的latency和success比較

不同 Ramp-up 情況下的服務連接情況(詳細說明都在word,實驗數據都在excel)
實驗目標：
觀察不同 Ramp-up 設定是否能降低瞬間流量對 Spring Boot / Tomcat / DB 的衝擊。

實驗結論：
越高的 request 數量會導致 Error rate 更高。在相同 request、不同 Ramp-up 的比較中，request 2000 符合「較高 Ramp-up 可以降低 Error rate」的推論。但 request 4000 在 0.5 秒和 1 秒 Ramp-up 的結果沒有明顯符合此推論。推測可能是因為 request 數量過高，導致 Tomcat 或 thread pool 已經無法承受，因此 0.5 秒與 1 秒 Ramp-up 差異不大。

不同Backoff+Jitter參數的latency、conflict比較(詳細說明都在word,實驗數據都在excel)
實驗目標：
比較不同 backoff 策略對 conflict ratio 和 latency 的影響。

實驗結論：
backoff + jitter 的 conflict/request 比例為 3.2%
exponential backoff 的 conflict/request 比例為 5.4%
exponential backoff + jitter 的 conflict/request 比例為 0.2%
其中 exponential backoff + jitter 不只是碰撞較少，latency 平均值、中位數與 PR99 也都是三者中最低。我認為原因是 exponential backoff + jitter 擁有最大的 jitter 空間，理論上能最大程度錯開請求碰撞，並減少 retry 造成的耗時。

樂觀鎖vs悲觀鎖_latency和success比較(詳細說明都在word,實驗數據都在excel)
實驗目標：
比較 Optimistic Locking 與 Pessimistic Locking 在高併發發券情境下的差異。

實驗結論：
原先預設Pessimistic Locking的latency會比Optimistic Locking的高，且Optimistic Locking的throughput比Pessimistic Locking高，因為Pessimistic Locking需要經歷完整的transaction，而Optimistic Locking則只要對照Version即可，但實驗數據與預設不同，推測原因有三，第一是transaction並沒有很複雜，導致Pessimistic Locking雖經歷完整的transaction但latency仍然不高，第二是因為Optimistic Locking會出現conflict/retry，提高Optimistic Locking的latency和throughput，第三是資料庫放在本機，沒有Server遠端協作，故在資料傳輸的latency也會比理論上的條件短很多，導致實驗結果的數據與理論不相同。



隔離層級比較和理解(詳細說明都在word)
實驗目標：
在實作 Optimistic Locking、Pessimistic Locking 與高併發優惠券系統後，進一步整理資料庫 Isolation Level 的概念，理解不同隔離級別所能避免的 Transaction Anomaly，以及其與 MVCC、SSI、Locking 機制之間的關係。

本研究重點包含：
- Dirty Read
- Non-repeatable Read
- Read Skew
- Write Skew
- Lost Update

並分析：
- Read Committed
- Repeatable Read
- Serializable

實驗結論：

Isolation Level 本質上是在定義：「Transaction 之間允許看到什麼資料，以及不允許出現哪些異常。」而不是直接規定資料庫如何實作。
在 PostgreSQL 中：
- Read Committed 為預設隔離級別
- Repeatable Read 主要透過 MVCC 與 Snapshot Isolation 實現
- Serializable 透過 SSI (Serializable Snapshot Isolation) 實現
研究過程中發現：Isolation Level、MVCC、Locking 並不是同一層概念。
可區分為：
- Isolation Level(規則層)：定義 transaction 之間允許看到什麼、不允許出現哪些異常
Concurrency Control(實現層)：資料庫用來達成隔離級別的技術，MVCC、SSI、2PL 等
- Lock(應用層)：Row Lock、Range Lock 等同步工具

本專案實作的優惠券超發問題，本質上屬於 Lost Update 類型的併發異常。

Optimistic Locking 透過Version欄位偵測衝突，
Pessimistic Locking 透過Row Lock限制併發更新，
而Serializable則由資料庫保證最終結果等價於循序執行。

透過整理，將資料庫理論與實際優惠券發放系統中的併發控制機制建立對應關係，對後續分析高併發系統設計理解有幫助。


Tomcat thread pool vs DB connection pool概念+監控
實驗目標：


實驗結論：



Tomcat thread pool vs DB connection thread實驗
實驗目標：


實驗結論：









Pessimistic Locking Timeout / Deadlock 實驗
實驗目標：
觀察 Pessimistic Locking 在高併發或鎖等待情境下，是否可能造成 timeout 或 deadlock 類型問題。


實驗結論：



# 9. Known Issues 

高併發測試受到Tomcat thread pool、DB connection pool 影響
Pessimistic Locking 需要補充 timeout / deadlock 測試
retry 與 transaction 結構仍可再調整，SQL查詢效率的部分
result code 目前仍使用字串，後續可改為 enum

# 10. Future Work

調整 Tomcat thread pool 與 DB connection pool 並進行比較
補 Pessimistic Locking timeout / deadlock 實驗
加入 Nginx rate limiting 作為 API 前層保護
補充更多 latency 圖表與 benchmark 視覺化
將 result code 改為 enum


# 11. How to Run  

Start Redis
docker run -p 6379:6379 redis
Start Spring Boot
./mvnw spring-boot:run
Claim Coupon API
POST /api/v1/coupon/{campaignId}/claim/optimistic  樂觀鎖用
POST /api/v1/coupon/{campaignId}/claim/pessimistic 悲觀鎖用

Request body:

{
  "userId": 1001,
  "requestId": "req-001"
}

Response:

{
  "result": "SUCCESS"
}

Test Method(測試方法)
使用 Docker 啟動 Redis
使用 PostgreSQL 儲存 coupon_campaign 與 user_coupon
使用 JMeter 模擬高併發請求
使用 Excel 整理 benchmark 數據
使用 Word 文件記錄詳細實驗過程與分析




