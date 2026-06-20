# 任務規格：api-gateway（Spring Cloud Gateway）

## 背景/動機

這是「OpsBoard Microservices Demo」的第五個任務。`incident-service`（核心 CRUD + timeline + runbook）跟 `ops-service`（health/metrics/trigger-alert + Redis）都已經完成並可以透過各自的 port（8081、8082）直接呼叫。

這次要建立 `api-gateway`——4 個應用之一，負責把所有對外的 API 請求統一轉送到對應的後端服務。**之後 `frontend-dashboard` 只會呼叫這個 gateway，不會直接連 incident-service 或 ops-service**，所以這個任務做完後，整個後端 API 的對外入口就統一了。

## 具體需求

### 1. 專案結構

在 repo 根目錄新增 `api-gateway/`，獨立的 Maven 專案。

- Spring Boot 版本跟 `incident-service`/`ops-service` 一致，用 `3.3.4`（parent `spring-boot-starter-parent`）。
- 加入 `spring-cloud-starter-gateway` 依賴。**這個依賴需要透過 `spring-cloud-dependencies` BOM 管理版本**，請在 `pom.xml` 的 `<dependencyManagement>` 加入：
  ```xml
  <dependencyManagement>
      <dependencies>
          <dependency>
              <groupId>org.springframework.cloud</groupId>
              <artifactId>spring-cloud-dependencies</artifactId>
              <version>2023.0.3</version>
              <type>pom</type>
              <scope>import</scope>
          </dependency>
      </dependencies>
  </dependencyManagement>
  ```
  `2023.0.3` 是對應 Spring Boot 3.2.x/3.3.x 的相容版本。**如果編譯或啟動時發現版本不相容**（例如 bean 衝突、啟動失敗），可以嘗試 `2023.0.x` 系列的其他 patch 版本（例如 `2023.0.4`、`2023.0.5`），但不要跳到 `2024.0.x` 系列（那是對應 Boot 3.4.x 的，會跟我們專案統一用的 3.3.4 衝突）。如果嘗試多個 patch 版本都解不開相容性問題，請在回報時明確說明卡在哪裡，不要硬是降版或改動其他服務的 Boot 版本來解決。
- **注意：Spring Cloud Gateway 是建立在 WebFlux（reactive）之上的**，所以**不要**額外加入 `spring-boot-starter-web`（會跟 reactive web server 衝突），`spring-cloud-starter-gateway` 已經帶入需要的 reactive 依賴。

### 2. 路由設定（`application.yml`）

用 Spring Cloud Gateway 的 config 驅動路由（不需要寫 Java `RouteLocator` bean，用 yml 設定即可），兩條路由：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: incident-service
          uri: ${INCIDENT_SERVICE_BASE_URL:http://localhost:8081}
          predicates:
            - Path=/api/incidents/**
          filters:
            - StripPrefix=1
        - id: ops-service
          uri: ${OPS_SERVICE_BASE_URL:http://localhost:8082}
          predicates:
            - Path=/api/ops/**
          filters:
            - StripPrefix=1
```

`StripPrefix=1` 的效果：請求 `/api/incidents/123` 轉送到後端時變成 `/incidents/123`（去掉第一段 `/api`）。請確認這個行為是對的——`/api/ops/services/health` 轉送後端時要變成 `/services/health`。

`server.port` 設為 `8080`。

### 3. CORS 設定

用一個 `@Configuration` class 設定全域 CORS（Spring Cloud Gateway 的 reactive CORS 設定，可以用 `CorsWebFilter` + `UrlBasedCorsConfigurationSource`，或是在 `application.yml` 用 `spring.cloud.gateway.globalcors` 設定，兩種方式都可以，挑你覺得更簡潔的）：

- 允許的 origin：從環境變數 `FRONTEND_ORIGIN` 讀取，預設值 `http://localhost:5173`
- 允許的 method：`GET`、`POST`、`PUT`、`OPTIONS`
- 允許常見 headers（例如 `Content-Type`）
- 套用到所有路徑（`/**`）

### 4. Logging filter

寫一個簡單的 `GlobalFilter`（搭配適當的 `Ordered` 優先序），對每個經過 gateway 的請求記錄一行 log，至少包含：HTTP method、請求路徑、回應狀態碼、處理耗時（毫秒）。用標準的 `Logger`（例如 `org.slf4j.Logger`）輸出即可，不需要寫到檔案或串接其他系統。

### 5. 設定與 Docker

- env var：`INCIDENT_SERVICE_BASE_URL`（預設 `http://localhost:8081`）、`OPS_SERVICE_BASE_URL`（預設 `http://localhost:8082`）、`FRONTEND_ORIGIN`（預設 `http://localhost:5173`）
- 新增 `api-gateway/Dockerfile`：跟其他兩個服務一樣的 multi-stage 模式（Maven 編譯 + JRE 17 runtime），expose `8080`。
- 修改根目錄 `docker-compose.yml`，新增 `api-gateway` 服務：
  - `container_name: opsboard-gateway`
  - port mapping：`127.0.0.1:8080:8080`
  - `depends_on`：`incident-service`、`ops-service`（一般 depends_on，不需要 `condition`）
  - 環境變數覆寫：`INCIDENT_SERVICE_BASE_URL=http://incident-service:8081`、`OPS_SERVICE_BASE_URL=http://ops-service:8082`（`FRONTEND_ORIGIN` 維持預設值即可，docker-compose 裡不用特別覆寫）

## 驗收標準

驗證指令前面記得加：
```
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:Path = "$env:JAVA_HOME\bin;C:\Tools\apache-maven-3.9.16\bin;C:\Program Files\Docker\Docker\resources\bin;$env:Path"
```

1. `mvn -f api-gateway clean package` 編譯成功。
2. `docker compose up -d mysql redis incident-service ops-service api-gateway`，5 個服務都正常啟動，沒有重啟迴圈，沒有 port 衝突。
3. `curl http://127.0.0.1:8080/api/incidents` → `200`，回應內容跟直接呼叫 `curl http://127.0.0.1:8081/incidents` 的內容一致（同一份資料，只是經過 gateway 轉送）。
4. `curl http://127.0.0.1:8080/api/ops/services/health` → `200`，回應內容跟直接呼叫 `curl http://127.0.0.1:8082/services/health` 一致。
5. `curl -X POST http://127.0.0.1:8080/api/ops/services/checkout-api/trigger-alert` → `201`（注意這個服務可能還在前一個任務驗證時留下的 cooldown 裡，如果回 409 也算正常，代表轉送有正確生效，可以等 60 秒後重試一次確認能拿到 201）。
6. 用以下指令模擬 CORS preflight 請求，確認回應 header 帶有 `Access-Control-Allow-Origin: http://localhost:5173`：
   ```
   curl -X OPTIONS http://127.0.0.1:8080/api/incidents -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: GET" -i
   ```
7. 檢查 api-gateway 容器的 log（`docker compose logs api-gateway`），確認看得到剛剛幾次請求的 method/path/status/耗時記錄。
8. `docker compose down -v` 清理乾淨。

## 排除範圍

- 不做 rate limiting。
- 不做任何 authentication/authorization filter。
- 不做服務發現（service discovery），路由的後端位址用環境變數/設定檔寫死即可（這也是專案明確排除 Eureka 等服務發現工具的延伸）。
- 不做比 `StripPrefix` 更複雜的路徑改寫規則。
- 不動 `incident-service/`、`ops-service/` 的程式碼。
- 不做 `frontend-dashboard/`。
- 不寫文件。
