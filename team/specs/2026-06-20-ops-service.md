# 任務規格：ops-service（health + metrics + trigger-alert + Redis）

## 背景/動機

這是「OpsBoard Microservices Demo」的第四個任務。前三個任務已經完成 `incident-service` 的完整 P0 功能（核心 CRUD、timeline、runbook，runbook 已經 seed 好 `payment-api`/`checkout-api`/`notification-worker` 三個服務名稱）。

這次要建立 `ops-service`——負責模擬「監控 3 個服務的健康狀態」並在使用者觸發 alert 時，呼叫 `incident-service` 建立對應的 incident。這個服務也是整個專案裡 Redis 唯一的實際使用者（做 alert cooldown）。

**這 3 個監控服務的名稱必須跟 incident-service 已經 seed 好的 runbook 完全一致**，這是跨任務的硬性協議，不可自己改名。

## 具體需求

### 1. 專案結構

在 repo 根目錄新增 `ops-service/`，獨立的 Maven 專案（Spring Boot 3.3.x + Java 17，跟 `incident-service` 用同一個 parent 版本以確保 `RestClient` 可用）。

Maven 依賴至少包含：`spring-boot-starter-web`、`spring-boot-starter-data-redis`、`spring-boot-starter-validation`。呼叫 incident-service 用 Spring 內建的 `RestClient`（Boot 3.2+ 提供），不需要額外引入 WebFlux 或第三方 HTTP client。

套件分層：
```
com.opsboard.ops
├── controller
├── service
├── dto
├── config
└── exception
```

### 2. 固定的 3 個監控服務

寫死在一個常數設定（可以用一個簡單的 enum 或 `Map`/`List` 常數，不需要存資料庫，也不需要做成可設定）：

| serviceName | 固定基準狀態 |
|---|---|
| `payment-api` | `HEALTHY` |
| `checkout-api` | `DEGRADED` |
| `notification-worker` | `HEALTHY` |

**`checkout-api` 永遠是 degraded，不要做成隨機**——demo 的腳本依賴這個服務固定可以被觸發 alert，請不要引入隨機決定「哪個服務 degraded」的邏輯。

### 3. `GET /services/health`

回 `200`，body 是 3 筆物件的陣列，每筆包含：
- `serviceName`：String
- `status`：String，值是 `"HEALTHY"` 或 `"DEGRADED"`（固定如上表）
- `lastCheckedAt`：Instant（每次呼叫都回傳當下時間，模擬「剛剛檢查過」）

### 4. `GET /services/{serviceName}/metrics`

- `serviceName` 不是上面 3 個之一 → `404`
- 是其中之一 → `200`，body：
  - `serviceName`：String
  - `latencyMs`：int，每次呼叫都重新隨機產生（用一點 jitter）
  - `errorRatePercent`：double
  - `cpuPercent`：double
  - `timestamp`：Instant

數值範圍規則：
- `HEALTHY` 的服務：`latencyMs` 落在 20-80 之間、`errorRatePercent` 落在 0.0-2.0 之間、`cpuPercent` 落在 10.0-40.0 之間。
- `DEGRADED` 的服務（即 `checkout-api`）：`latencyMs` 落在 500-1500 之間、`errorRatePercent` 落在 15.0-35.0 之間、`cpuPercent` 落在 70.0-95.0 之間。

（這個區間設計是為了讓 dashboard 畫面上一看數字就知道哪個服務有問題，數值本身不需要多精確，落在範圍內、每次呼叫有變化即可。）

### 5. `POST /services/{serviceName}/trigger-alert`

- `serviceName` 不是上述 3 個之一 → `404`
- 是其中之一，進入 Redis cooldown 檢查：
  - Redis key：`alert:cooldown:{serviceName}`
  - 用 `SET key value NX EX 60`（key 不存在才設置成功，TTL 60 秒）
  - **如果 key 已存在**（代表還在 cooldown 中）→ 回 `409`，body：
    ```json
    { "error": "cooldown_active", "retryAfterSeconds": <用 Redis TTL 指令查出目前剩餘秒數> }
    ```
    這個情況**不要**呼叫 incident-service，直接回應。
  - **如果 key 原本不存在**（成功設置 cooldown）→ 取得當下這個服務的 metrics 快照（重用第 4 點的邏輯產生一份數值），組合呼叫 incident-service 建立 incident：
    - `POST {incident-service base url}/incidents`
    - body：
      - `title`：例如 `"{serviceName} health alert triggered"`
      - `description`：把這次快照的 `latencyMs`/`errorRatePercent`/`cpuPercent` 數值組成一段說明文字
      - `serviceName`：原樣帶入
      - `severity`：固定帶 `"HIGH"`（不需要動態決定，demo 用固定值即可）
    - incident-service 回 `201` 成功 → ops-service 回 `201`，body 至少包含 incident-service 回應裡的 `id`（可以直接把 incident-service 的回應原樣轉發，或包一層都可以，只要至少有 `id` 欄位）
    - incident-service 呼叫失敗（連線失敗、逾時、非 2xx）→ ops-service 回 `502`，body 帶簡短錯誤訊息。**這個情況不用特別把 Redis 的 cooldown key 刪除**——demo 環境下這個 edge case 不需要做到完全一致性，cooldown 60 秒到期後自然恢復即可，不需要額外的回滾邏輯。

### 6. 設定與 Docker

- env var：
  - Redis 連線：`REDIS_HOST`（預設 `localhost`，docker-compose 裡覆寫成 `redis`）、`REDIS_PORT`（預設 `6379`）
  - incident-service base URL：`INCIDENT_SERVICE_BASE_URL`（預設 `http://localhost:8081`，docker-compose 裡覆寫成 `http://incident-service:8081`）
- `server.port` 設為 `8082`
- 新增 `ops-service/Dockerfile`：跟 `incident-service/Dockerfile` 一樣的 multi-stage 模式（Maven 編譯 + JRE 17 runtime），expose `8082`。
- 修改根目錄 `docker-compose.yml`，新增 `ops-service` 服務：
  - `container_name: opsboard-ops-service`
  - port mapping：`127.0.0.1:8082:8082`（沿用既有「只綁定 localhost」慣例）
  - `depends_on`：`redis`（等 healthy）、`incident-service`（這個服務目前沒有 healthcheck，直接用一般的 depends_on 即可，不用加 `condition`）
  - 對應的環境變數覆寫（host 用 `redis`、incident-service base url 用 `http://incident-service:8081`）

## 驗收標準

驗證指令前面記得加：
```
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:Path = "$env:JAVA_HOME\bin;C:\Tools\apache-maven-3.9.16\bin;C:\Program Files\Docker\Docker\resources\bin;$env:Path"
```

1. `mvn -f ops-service clean package` 編譯成功。
2. `docker compose up -d mysql redis incident-service ops-service`，4 個服務都正常啟動，沒有重啟迴圈。
3. `curl http://127.0.0.1:8082/services/health` → `200`，剛好 3 筆，`checkout-api` 的 `status` 是 `"DEGRADED"`，另外兩個是 `"HEALTHY"`。
4. `curl http://127.0.0.1:8082/services/checkout-api/metrics` 連續呼叫 2 次 → 兩次數值都落在 degraded 區間（`latencyMs` ≥ 500），且兩次數值不完全相同（有 jitter）。
5. `curl http://127.0.0.1:8082/services/unknown-service/metrics` → `404`。
6. `curl -X POST http://127.0.0.1:8082/services/checkout-api/trigger-alert` → `201`，body 有 `id`；接著直接查 `curl http://127.0.0.1:8081/incidents`（incident-service）確認剛建立的 incident 存在，`serviceName` 是 `checkout-api`、`severity` 是 `HIGH`。
7. **立刻**再對同一個服務送一次 `POST .../checkout-api/trigger-alert` → `409`，body 有 `retryAfterSeconds`，數值應該接近 60（小於等於 60、大於 0）。
8. `curl -X POST http://127.0.0.1:8082/services/unknown-service/trigger-alert` → `404`。
9. `docker compose down -v` 清理乾淨。

## 排除範圍

- 不做 service health 的歷史記錄或趨勢圖。
- 不做可設定的監控服務清單（寫死 3 個即可，不用做成資料庫或設定檔可調整）。
- 不做 alert 觸發後的自動清除 cooldown 機制，60 秒 TTL 自然過期即可。
- 不動 `frontend-dashboard/`、`api-gateway/`、`incident-service/`（除非是讀取既有 API，不要修改 incident-service 的程式碼）。
- 不寫文件。
