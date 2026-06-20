# API Overview

所有 endpoint 都是透過 `api-gateway` 呼叫，base URL 為 `http://localhost:8080`。前端與外部呼叫者都只應該打 `/api/...` 這個層級的路徑，不應該直連 `incident-service`（8081）或 `ops-service`（8082）的原始 port。

路由規則（定義在 `api-gateway/src/main/resources/application.yml`）：
- `/api/incidents/**`、`/api/runbooks/**` → 去掉 `/api` 前綴後轉給 `incident-service`。
- `/api/ops/**` → 去掉 `/api/ops` 前綴後轉給 `ops-service`（也就是 `/api/ops/services/health` 實際打到 ops-service 的 `/services/health`）。

## Incident 相關

### `POST /api/incidents` — 建立 incident

| 項目 | 內容 |
| --- | --- |
| Method | POST |
| 路徑 | `/api/incidents` |
| 說明 | 建立一筆新的 incident，狀態固定從 `OPEN` 開始，並自動寫入第一筆 timeline entry。 |

Request body 範例：

```json
{
  "title": "Test incident",
  "description": "desc",
  "serviceName": "payment-api",
  "severity": "HIGH"
}
```

`severity` 必須是 `LOW`/`MEDIUM`/`HIGH`/`CRITICAL` 之一；`title`、`serviceName` 為必填，`description` 可省略。

成功回應範例（`201 Created`）：

```json
{
  "id": 3,
  "title": "Test incident",
  "description": "desc",
  "serviceName": "payment-api",
  "severity": "HIGH",
  "status": "OPEN",
  "createdAt": "2026-06-20T07:17:37.953148834Z",
  "updatedAt": "2026-06-20T07:17:37.953148834Z"
}
```

可能的錯誤狀態碼：
- `400 Bad Request`：缺少必填欄位或欄位格式錯誤（例如 `title` 空白、`severity` 缺漏），回應格式：
  ```json
  {"status":400,"error":"Bad Request","message":"title: must not be blank, severity: must not be null, serviceName: must not be blank"}
  ```

### `GET /api/incidents` — 列出所有 incident

| 項目 | 內容 |
| --- | --- |
| Method | GET |
| 路徑 | `/api/incidents` |
| 說明 | 回傳所有 incident，依建立時間新到舊排序。 |

成功回應範例（`200 OK`）：

```json
[
  {
    "id": 3,
    "title": "Test incident",
    "description": "desc",
    "serviceName": "payment-api",
    "severity": "HIGH",
    "status": "INVESTIGATING",
    "createdAt": "2026-06-20T07:17:37.953149Z",
    "updatedAt": "2026-06-20T07:17:48.614368619Z"
  }
]
```

無 incident 時回傳空陣列 `[]`。沒有額外的錯誤狀態碼（永遠 200）。

### `GET /api/incidents/{id}` — 取得單筆 incident

| 項目 | 內容 |
| --- | --- |
| Method | GET |
| 路徑 | `/api/incidents/{id}` |
| 說明 | 依 ID 取得單一 incident 詳細資料。 |

成功回應範例（`200 OK`）：同上單筆 incident 物件。

可能的錯誤狀態碼：
- `404 Not Found`：找不到對應 ID 的 incident。
  ```json
  {"status":404,"error":"Not Found","message":"Incident with id 9999 not found"}
  ```

### `PUT /api/incidents/{id}/status` — 更新 incident 狀態

| 項目 | 內容 |
| --- | --- |
| Method | PUT |
| 路徑 | `/api/incidents/{id}/status` |
| 說明 | 推進 incident 狀態機，只允許依序 `OPEN → INVESTIGATING → MITIGATED → RESOLVED`，不允許跳躍或回退。成功時自動寫入一筆新的 timeline entry。 |

Request body 範例：

```json
{"status": "INVESTIGATING"}
```

成功回應範例（`200 OK`）：

```json
{
  "id": 3,
  "title": "Test incident",
  "description": "desc",
  "serviceName": "payment-api",
  "severity": "HIGH",
  "status": "INVESTIGATING",
  "createdAt": "2026-06-20T07:17:37.953149Z",
  "updatedAt": "2026-06-20T07:17:48.614368619Z"
}
```

可能的錯誤狀態碼：
- `404 Not Found`：找不到對應 ID 的 incident。
- `409 Conflict`：請求的狀態不是目前狀態的合法下一步（例如從 `INVESTIGATING` 想直接跳到 `RESOLVED`，或對已經 `RESOLVED` 的 incident 再送任何狀態）。
  ```json
  {"status":409,"error":"Conflict","message":"Cannot transition incident from status INVESTIGATING to RESOLVED"}
  ```
- `400 Bad Request`：`status` 欄位缺漏或不是合法的 enum 值。

### `GET /api/incidents/{id}/timeline` — 取得 incident 的 timeline

| 項目 | 內容 |
| --- | --- |
| Method | GET |
| 路徑 | `/api/incidents/{id}/timeline` |
| 說明 | 取得該 incident 從建立到現在所有的狀態變更紀錄，依時間舊到新排序。 |

成功回應範例（`200 OK`）：

```json
[
  {
    "id": 8,
    "incidentId": 3,
    "fromStatus": null,
    "toStatus": "OPEN",
    "note": "Incident created",
    "createdAt": "2026-06-20T07:17:37.973223Z"
  },
  {
    "id": 9,
    "incidentId": 3,
    "fromStatus": "OPEN",
    "toStatus": "INVESTIGATING",
    "note": "Status changed to INVESTIGATING",
    "createdAt": "2026-06-20T07:17:48.614Z"
  }
]
```

可能的錯誤狀態碼：
- `404 Not Found`：找不到對應 ID 的 incident。

## Runbook 相關

### `GET /api/runbooks?serviceName={name}` — 查詢服務對應的 runbook

| 項目 | 內容 |
| --- | --- |
| Method | GET |
| 路徑 | `/api/runbooks` |
| Query 參數 | `serviceName`（必填），例如 `payment-api`、`checkout-api`、`notification-worker` |
| 說明 | 查詢指定監控服務的應變手冊內容（系統啟動時由 `RunbookSeeder` 預先 seed 進資料庫，每個服務目前固定 1 筆）。 |

成功回應範例（`200 OK`）：

```json
[
  {
    "id": 1,
    "serviceName": "payment-api",
    "title": "Payment API Service Degradation Runbook",
    "content": "1. Check the status dashboard for the upstream payment gateway...\n2. Inspect payment-api logs..."
  }
]
```

查無對應服務的 runbook 時回傳空陣列 `[]`（不會是 404）。

可能的錯誤狀態碼：
- `400 Bad Request`：缺少必填的 `serviceName` query 參數。
  ```json
  {"status":400,"error":"Bad Request","message":"Required request parameter 'serviceName' for method parameter type String is not present"}
  ```

## Ops 相關

### `GET /api/ops/services/health` — 取得 3 個監控服務的健康狀態

| 項目 | 內容 |
| --- | --- |
| Method | GET |
| 路徑 | `/api/ops/services/health` |
| 說明 | 回傳固定 3 個監控服務（`payment-api`、`checkout-api`、`notification-worker`）目前的健康狀態。狀態是固定 baseline（`checkout-api` 固定為 `DEGRADED`，其餘固定為 `HEALTHY`），不依賴 Redis，Redis 故障時此 endpoint 仍可正常回應。 |

成功回應範例（`200 OK`）：

```json
[
  {"serviceName":"payment-api","status":"HEALTHY","lastCheckedAt":"2026-06-20T07:17:25.430668293Z"},
  {"serviceName":"checkout-api","status":"DEGRADED","lastCheckedAt":"2026-06-20T07:17:25.430668293Z"},
  {"serviceName":"notification-worker","status":"HEALTHY","lastCheckedAt":"2026-06-20T07:17:25.430668293Z"}
]
```

沒有額外的錯誤狀態碼（永遠 200）。

### `GET /api/ops/services/{serviceName}/metrics` — 取得服務的指標快照

| 項目 | 內容 |
| --- | --- |
| Method | GET |
| 路徑 | `/api/ops/services/{serviceName}/metrics` |
| 說明 | 針對指定服務產生一份隨機但符合健康/degraded 區間的指標快照（latency、error rate、CPU），每次呼叫都會重新隨機產生，不是真實監控數據。 |

成功回應範例（`200 OK`）：

```json
{
  "serviceName": "payment-api",
  "latencyMs": 77,
  "errorRatePercent": 1.3650681583411328,
  "cpuPercent": 33.235382622005815,
  "timestamp": "2026-06-20T07:17:25.741787382Z"
}
```

可能的錯誤狀態碼：
- `404 Not Found`：`serviceName` 不是 `payment-api`/`checkout-api`/`notification-worker` 之一。
  ```json
  {"error":"Unknown service: unknown-service"}
  ```

### `POST /api/ops/services/{serviceName}/trigger-alert` — 觸發 alert（建立 incident）

| 項目 | 內容 |
| --- | --- |
| Method | POST |
| 路徑 | `/api/ops/services/{serviceName}/trigger-alert` |
| 說明 | 觸發指定服務的告警：先檢查 Redis 是否已有 60 秒內的 cooldown 鎖，沒有鎖則產生指標快照並呼叫 incident-service 建立一筆新 incident（severity 固定 `HIGH`），同時把 cooldown 鎖寫入 Redis（TTL 60 秒）。沒有 request body。 |

成功回應範例（`201 Created`，建立了新 incident）：

```json
{
  "id": 4,
  "title": "checkout-api health alert triggered",
  "description": "Automated alert triggered for checkout-api. Snapshot metrics: latencyMs=1311, errorRatePercent=29.55, cpuPercent=87.11",
  "serviceName": "checkout-api",
  "severity": "HIGH",
  "status": "OPEN",
  "createdAt": "2026-06-20T07:18:09.349054140Z",
  "updatedAt": "2026-06-20T07:18:09.349054140Z"
}
```

可能的錯誤狀態碼：
- `409 Conflict`：60 秒 cooldown 還在生效中，不會建立新 incident。
  ```json
  {"error":"cooldown_active","retryAfterSeconds":59}
  ```
- `404 Not Found`：`serviceName` 不是 `payment-api`/`checkout-api`/`notification-worker` 之一。
  ```json
  {"error":"Unknown service: unknown-svc"}
  ```
- `502 Bad Gateway`：cooldown 通過但呼叫 incident-service 建立 incident 失敗時，由 ops-service 的 `GlobalExceptionHandler` 轉成此狀態碼（正常運作下不會發生，僅在 incident-service 異常時觸發；實際排查方式見 `docs/runbook.md`）。
- `500 Internal Server Error`：Redis 連線異常導致 cooldown 檢查本身失敗時的未捕捉例外（實測會發生在 Redis 容器停機時，回應沒有 `message` 欄位，是 Spring Boot 預設錯誤頁，詳見 `docs/runbook.md` 排查情境 2）。

## 路徑找不到時的 Gateway 層級錯誤

如果呼叫的路徑沒有對應任何路由規則（例如打 `/api/不存在的路徑`），會直接由 api-gateway 回應，**不會**到任何後端服務：

```json
{"timestamp":"2026-06-20T07:17:58.700+00:00","path":"/api/nonexistent","status":404,"error":"Not Found","requestId":"f9dc701e-14"}
```

這個格式跟 incident-service/ops-service 自己回的錯誤格式不同（沒有 `message` 欄位），可以用來判斷問題出在「沒打到任何服務」還是「打到服務但業務邏輯回錯」。
