# 任務規格：incident-service 核心 CRUD

## 背景/動機

這是「OpsBoard Microservices Demo」的第二個任務。上一個任務（`team/specs/2026-06-20-repo-scaffold.md`）已經建好 `docker-compose.yml`（含 mysql + redis，mysql 已建好 `incidentdb` 資料庫，連線資訊見根目錄 `.env.example`）。

這次要建立 `incident-service`——4 個微服務之一，負責 incident（事故）的核心 CRUD。這個服務之後還會在另一個任務裡擴充 timeline 與 runbook 功能，**這次先不做那兩塊**，只做最核心的 incident 建立/查詢/狀態更新。

## 具體需求

### 1. 專案結構

在 repo 根目錄新增 `incident-service/`，是一個獨立的 Maven 專案（Spring Boot 3.x + Java 17）。

Maven 依賴至少包含：`spring-boot-starter-web`、`spring-boot-starter-data-jpa`、`mysql-connector-j` (或官方目前建議的 MySQL JDBC driver artifact)、`spring-boot-starter-validation`。

套件分層（package 結構）：
```
com.opsboard.incident
├── controller
├── service
├── repository
├── dto
├── entity
└── exception
```

### 2. `Incident` entity

欄位：
- `id`：Long，自動產生的主鍵
- `title`：String，必填，非空白
- `description`：String，可為 null
- `serviceName`：String，必填，非空白（代表這個 incident 是關於哪個 monitored service）
- `severity`：enum，值為 `LOW` / `MEDIUM` / `HIGH` / `CRITICAL`，必填
- `status`：enum，值為 `OPEN` / `INVESTIGATING` / `MITIGATED` / `RESOLVED`
- `createdAt`：Instant，建立時由 server 端設定，不可由 client 指定
- `updatedAt`：Instant，每次更新時由 server 端設定

### 3. DTO

- `CreateIncidentRequest`：`title`（必填非空白）、`description`（可選）、`serviceName`（必填非空白）、`severity`（必填，合法 enum 值）。**不允許帶 `status`**——新建立的 incident 一律由 server 端強制設為 `OPEN`，即使 request body 裡帶了 `status` 欄位也要忽略。
- `IncidentResponse`：包含 `id`、`title`、`description`、`serviceName`、`severity`、`status`、`createdAt`、`updatedAt`。
- `UpdateIncidentStatusRequest`：只有一個欄位 `status`（必填，合法 enum 值）。

### 4. Endpoints

- `POST /incidents`
  - body: `CreateIncidentRequest`
  - 成功回 `201 Created`，body 為 `IncidentResponse`，`status` 一律是 `OPEN`
  - body validation 失敗（例如 `title` 空白、`severity` 不是合法 enum 值）回 `400`

- `GET /incidents`
  - 回 `200`，body 為 `IncidentResponse` 陣列，依 `createdAt` 新到舊排序
  - 沒有資料時回空陣列 `[]`，不是錯誤

- `GET /incidents/{id}`
  - 找到回 `200` + `IncidentResponse`
  - 找不到回 `404`，body 為錯誤格式（見下方「錯誤格式」）

- `PUT /incidents/{id}/status`
  - body: `UpdateIncidentStatusRequest`
  - **狀態變更規則：強制逐步推進**，合法的下一個狀態只有當前狀態的下一階：`OPEN → INVESTIGATING`、`INVESTIGATING → MITIGATED`、`MITIGATED → RESOLVED`。
  - 如果請求的新狀態不是「當前狀態的下一階」（包括跳級、回退、設成相同狀態），回 `409 Conflict`，錯誤訊息要說明目前狀態與請求的狀態。
  - 如果 `status` 欄位不是合法的 enum 值，回 `400`。
  - 找不到該 id 的 incident，回 `404`。
  - 成功時回 `200` + 更新後的 `IncidentResponse`，並把 `updatedAt` 更新成當下時間。

### 5. 錯誤格式

所有錯誤回應統一用以下 JSON 結構（用 `@RestControllerAdvice` 實作全域例外處理）：
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Incident with id 123 not found"
}
```
至少要處理：validation 失敗（400）、找不到資源（404）、非法狀態轉換（409）。

### 6. 設定與 Docker

- `application.yml`（或 `.properties`）的資料庫連線資訊全部走環境變數，變數名稱與預設值要跟根目錄 `.env.example` / `docker-compose.yml` 裡 mysql 的設定對齊（`MYSQL_DATABASE=incidentdb`、`MYSQL_USER=opsboard`、`MYSQL_PASSWORD=opsboard_pw`，host 用 `mysql`（docker-compose service name）、port `3306`）。本機不透過 docker-compose 直接跑（`mvn spring-boot:run`）時，要能透過環境變數覆寫成 `localhost:3306` 連線。
- JPA 設定：開發階段用 `spring.jpa.hibernate.ddl-auto=update`（之後任務若要改成正式 migration 工具，再另外處理，這次不需要）。
- 新增 `incident-service/Dockerfile`：multi-stage build（一個 stage 用 Maven 編譯產生 jar，另一個 stage 用精簡的 JRE 17 image 跑這個 jar），對外 expose port `8081`。
- 修改根目錄 `docker-compose.yml`，新增 `incident-service` 服務：
  - `container_name: opsboard-incident-service`
  - port mapping：`127.0.0.1:8081:8081`（沿用上一個任務「只綁定 localhost」的安全慣例）
  - `depends_on: mysql`，且要等 mysql `healthy` 才啟動（用 `condition: service_healthy`）
  - 環境變數從 `.env`/預設值帶入資料庫連線資訊，host 用 `mysql`

## 驗收標準

以下指令前面記得先把 Docker 加進 PATH：`$env:Path = "C:\Program Files\Docker\Docker\resources\bin;$env:Path"`；本機編譯/執行需要 Java/Maven 也在 PATH 上（JDK 裝在 `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`，Maven 裝在 `C:\Tools\apache-maven-3.9.16`）。

1. `mvn -f incident-service clean package` 編譯成功，產生可執行 jar。
2. 本機直接跑（不透過 docker，用 `mvn spring-boot:run` 或跑編譯出的 jar，搭配環境變數指向 `localhost:3306` 的 mysql——可以先用 `docker compose up -d mysql` 把資料庫跑起來再測）：
   - `POST /incidents`，body `{"title":"Checkout API 503s","serviceName":"checkout-api","severity":"HIGH"}` → `201`，回應裡 `status` 是 `"OPEN"`，有 `id` 跟兩個時間戳記。
   - 帶 `severity` 給不合法的值（例如 `"URGENT"`）→ `400`。
   - `GET /incidents` → `200`，陣列裡包含剛建立的 incident。
   - `GET /incidents/{剛建立的id}` → `200`，內容正確。
   - `GET /incidents/999999`（不存在的 id）→ `404`，body 符合錯誤格式。
   - `PUT /incidents/{id}/status`，body `{"status":"INVESTIGATING"}` → `200`，`status` 變成 `INVESTIGATING`。
   - 立刻再對同一個 incident 送 `PUT .../status` body `{"status":"OPEN"}`（回退）→ `409`。
   - 對同一個 incident 送 `PUT .../status` body `{"status":"RESOLVED"}`（跳級，當前是 `INVESTIGATING`）→ `409`。
3. `docker compose up -d mysql incident-service`：兩個服務都要能正常啟動（`docker compose ps` 顯示 incident-service 是 `running`/`healthy` 狀態，沒有重啟迴圈）。
4. 透過 docker 跑起來後，用 `curl http://127.0.0.1:8081/incidents` 能拿到 `200` 回應。
5. `docker compose down -v` 能正常清理，不留殘留容器/volume。

## 排除範圍

- 不做 `IncidentTimelineEntry`（timeline）功能，不做 `Runbook` entity/endpoint，這些是下一個任務（`incident-service-timeline-runbook`）的範圍。
- 不做 incident 列表的分頁、filter、搜尋功能。
- 不做 incident 的修改（title/description 編輯）或刪除功能。
- 不做 actuator health endpoint（這是 P1 範圍，目前不做）。
- 不動 `frontend-dashboard/`、`api-gateway/`、`ops-service/`，這些是其他任務的範圍。
- 不寫 README 或其他文件。
