# OpsBoard 維運手冊（Runbook）

## 重要：這份文件跟系統內建的 runbook 是兩個不同的東西

請先弄清楚這個區別，避免混淆：

- **這份文件**（`docs/runbook.md`）是給**維運 OpsBoard 這套系統本身**的人看的，內容是「OpsBoard 的容器跑不起來/連不上資料庫/API 回應異常時怎麼排查」，讀者是負責部署、維運這套 demo 系統的工程師。
- **系統內建的 runbook**（存在 `incident-service` 的 MySQL 資料庫裡，由 `RunbookSeeder` 在啟動時 seed 進去，透過 `GET /api/runbooks?serviceName=...` 查詢，並顯示在前端 incident 詳情頁的「Runbook」區塊）是**產品本身模擬的應變手冊**，內容是「`payment-api`/`checkout-api`/`notification-worker` 這 3 個被監控的服務發生健康問題時，假想的 on-call 工程師該怎麼處理」，是 demo 故事裡的展示內容，跟 OpsBoard 系統的維運完全無關。

簡單說：前者是「修 OpsBoard 這台車」，後者是「車上展示的儀表板教學內容」。以下排查情境都是針對前者。

## 前置準備

所有指令都在 repo 根目錄（`docker-compose.yml` 所在目錄）執行。Windows PowerShell 需要先把 Docker CLI 加進 PATH：

```powershell
$env:Path = "C:\Program Files\Docker\Docker\resources\bin;$env:Path"
```

## 排查情境 1：`incident-service` 啟動失敗 / 連不上 MySQL

**症狀**：`docker compose ps` 看到 `incident-service` 容器是 `Exited (1)` 狀態；或透過 gateway 打 `/api/incidents` 系列 API 一直收到 `500`/連線被拒。

**排查步驟**：

1. 確認 MySQL 容器是否健康：
   ```
   docker compose ps mysql
   ```
   如果 STATUS 不是 `Up ... (healthy)`，先處理 MySQL（檢查 `MYSQL_ROOT_PASSWORD` 等環境變數有沒有設錯、磁碟空間是否足夠）。
2. 看 `incident-service` 的 log：
   ```
   docker compose logs incident-service --tail 50
   ```
   實測過的典型錯誤訊息：
   ```
   o.h.engine.jdbc.spi.SqlExceptionHelper   : Communications link failure
   org.hibernate.exception.JDBCConnectionException: unable to obtain isolated JDBC connection [Communications link failure]
   Caused by: com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure
   ```
   看到這串訊息就代表 incident-service 在啟動時連不上 MySQL（已實際測試：把 `mysql` 容器停掉後重啟 `incident-service` 重現過這個錯誤），容器會直接以 exit code 1 結束，**不會重試**。
3. 確認 `docker-compose.yml` 裡 `incident-service` 的環境變數（`MYSQL_HOST=mysql`、`MYSQL_PORT=3306`、`MYSQL_DATABASE`/`MYSQL_USER`/`MYSQL_PASSWORD`）跟 `mysql` 服務的設定是否一致。
4. 修好 MySQL（或等它 healthcheck 通過）之後，重新啟動 incident-service：
   ```
   docker compose start incident-service
   ```
   incident-service 沒有自動重連機制，必須手動重啟容器。
5. 用 `docker compose logs incident-service --tail 20` 確認看到 `Started IncidentServiceApplication in X seconds` 才算真正啟動完成。

## 排查情境 2：`ops-service` 連不上 Redis

**症狀**：透過 gateway 打 `POST /api/ops/services/{name}/trigger-alert` 等了很久才收到 `500 Internal Server Error`（不是預期中的 `201`/`409`）。注意：`GET /api/ops/services/health` 不受影響，因為這個 endpoint 不需要查 Redis。

**排查步驟**：

1. 確認 Redis 容器健康狀態：
   ```
   docker compose ps redis
   ```
2. 看 `ops-service` 的 log：
   ```
   docker compose logs ops-service --tail 50
   ```
   實測過的典型錯誤訊息（把 `redis` 容器停掉後呼叫 trigger-alert 重現過）：
   ```
   org.springframework.dao.QueryTimeoutException: Redis command timed out
   Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 1 minute(s)
   ```
   或容器剛重啟、DNS 還沒解析出來時會看到：
   ```
   java.net.UnknownHostException: redis
   ```
   這個例外目前沒有被 `GlobalExceptionHandler` 特別處理，所以會以 Spring Boot 預設的 `500` 錯誤頁回應，**且因為 command timeout 預設要等 1 分鐘**，呼叫端會卡住很久才拿到錯誤——如果發現 trigger-alert 反應異常慢，先懷疑是 Redis 連不上，而不是 ops-service 卡死。
3. 重啟 Redis：
   ```
   docker compose start redis
   ```
   Redis 恢復後**不需要重啟 ops-service**，下一次呼叫 trigger-alert 就會恢復正常（已實測驗證）。

## 排查情境 3：透過 gateway 打 API 收到非預期回應（404/500），怎麼判斷斷在哪一段

**症狀**：呼叫 `http://localhost:8080/api/...` 收到非預期的狀態碼或錯誤格式。

**排查步驟**：

1. 先看錯誤回應的 JSON 格式，初步判斷是哪一層擋下來的：
   - 格式是 `{"timestamp":..., "path":"/api/xxx", "status":404, "error":"Not Found", "requestId":"..."}` （**沒有** `message` 欄位）→ 這是 **api-gateway 自己**的預設錯誤頁（Spring Cloud Gateway 找不到符合的路由規則），代表打的路徑沒有任何一條 `application.yml` 裡的 `Path` predicate 吃到（例如打錯 `/api/xxx` 但系統只定義了 `/api/incidents/**`、`/api/runbooks/**`、`/api/ops/**` 三條規則）。
   - 格式是 `{"status":404, "error":"Not Found", "message":"Incident with id 9999 not found"}` （**有** `message` 欄位，內容是業務語意）→ 這是請求成功路由到 **incident-service**，但業務邏輯判斷資源不存在，正常的應用層 404，不是斷線問題。
   - 格式是 `{"error":"Unknown service: xxx"}` → 路由到 **ops-service**，但 `serviceName` 不在 `payment-api`/`checkout-api`/`notification-worker` 這 3 個之列。
   - 格式是 `{"timestamp":..., "status":500, "error":"Internal Server Error", "path":"/api/incidents", "requestId":"..."}` （沒有 `message`）且發生在原本正常運作的 endpoint 上 → 通常代表 gateway 路由到的**後端服務本身連不上**（例如 incident-service/ops-service 容器掛掉），可以用 `docker compose ps` 確認對應服務是否還活著。
2. 看 `api-gateway` 的 log，確認請求有沒有被路由出去、實際打到哪個下游、耗時多久：
   ```
   docker compose logs api-gateway --tail 50
   ```
   gateway 對每個請求都會印一行 `METHOD PATH -> STATUS (耗時 ms)`（由 `LoggingGlobalFilter` 印出），可以用這行快速確認 gateway 有沒有收到請求、轉送後拿到什麼狀態碼。
3. 如果 gateway 端看起來轉送成功但狀態碼異常，再去看對應後端服務（`incident-service` 或 `ops-service`）的 log 找原始錯誤（參考情境 1、2 的做法）。
4. 確認 `docker-compose.yml` 裡 `api-gateway` 的 `INCIDENT_SERVICE_BASE_URL`/`OPS_SERVICE_BASE_URL` 環境變數有沒有被覆寫錯（正常應該是 `http://incident-service:8081`、`http://ops-service:8082`，用容器名稱而非 `localhost`）。

## 怎麼用 `docker compose logs` 看各服務的 log

```
# 看單一服務最近 N 行（最常用）
docker compose logs <service> --tail 50

# 持續追蹤（像 tail -f）
docker compose logs <service> -f

# 一次看全部服務交錯的 log（依時間排序，每行前面會標 service 名稱）
docker compose logs -f

# 確認所有容器目前的健康狀態
docker compose ps
```

其中 `<service>` 是 `docker-compose.yml` 裡的 service 名稱：`mysql`、`redis`、`incident-service`、`ops-service`、`api-gateway`、`frontend-dashboard`。

## 其他注意事項

- 所有服務的 port 都綁定在 `127.0.0.1`，只能從本機存取，這是預期行為，不是 bug。
- 如果整套系統行為很怪、懷疑是髒資料造成，最乾淨的處理方式是 `docker compose down -v` 完全清掉（包含 MySQL volume）再重新 `docker compose up -d --build`，詳見 `docs/demo-script.md` 最後一節。
