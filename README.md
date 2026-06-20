# OpsBoard Microservices Demo

OpsBoard 是一個模擬「服務監控 + incident 應變」場景的微服務 demo 系統，作為求職作品集使用，目的是展示後端工程能力：多服務協作、API Gateway 路由、關聯式資料庫與快取的實際應用場景（不是裝飾用），以及前後端整合的完整流程。系統模擬一個簡化版的 on-call 工作流程：監控 3 個服務的健康狀態，當其中一個服務異常時觸發告警建立 incident，並可以推進 incident 的處理狀態、查看處理歷程與對應的應變手冊。

## 架構總覽

系統由 **4 個應用服務 + 2 個基礎設施服務**組成，全部用 Docker Compose 啟動：

| 服務 | 角色 | Port（綁定在 127.0.0.1） |
| --- | --- | --- |
| `frontend-dashboard` | React 前端（dashboard 首頁 + incident 詳情頁），nginx serve 編譯後的靜態檔 | 5173 |
| `api-gateway` | Spring Cloud Gateway，統一路由 `/api/...` 到後端服務 | 8080 |
| `incident-service` | Incident CRUD、狀態機、timeline、runbook 查詢 | 8081 |
| `ops-service` | 服務健康狀態、指標、trigger-alert（Redis cooldown） | 8082 |
| `mysql` | incident-service 的關聯式資料庫 | 3306 |
| `redis` | ops-service 的 alert cooldown 狀態儲存 | 6379 |

詳細的服務職責、架構圖（Mermaid）與資料流說明請見 [`docs/architecture.md`](docs/architecture.md)。

## Quick Start

需要先安裝 Docker Desktop（含 Docker Compose）。在 repo 根目錄執行：

```powershell
# Windows PowerShell 如果 docker 指令不在 PATH 裡，先執行：
$env:Path = "C:\Program Files\Docker\Docker\resources\bin;$env:Path"

docker compose up -d --build
```

等待約 30-60 秒（首次 build 會更久）讓所有服務啟動完成，可以用 `docker compose ps` 確認 6 個容器都顯示 `Up`（`mysql`、`redis` 會顯示 `(healthy)`）。

啟動後：

- 打開 **`http://localhost:5173`** 看 dashboard。
- 所有 API 透過 gateway 呼叫，base URL 是 **`http://localhost:8080/api/...`**，例如：
  ```bash
  curl http://localhost:8080/api/incidents
  curl http://localhost:8080/api/ops/services/health
  ```

要重置成乾淨狀態（清空 incident 資料、重新從 0 開始）：

```bash
docker compose down -v
docker compose up -d --build
```

## 技術棧

- **後端**：Java 17、Spring Boot 3（`incident-service`、`ops-service`）、Spring Cloud Gateway（`api-gateway`）
- **資料層**：MySQL 8.0（incident 持久化）、Redis 7（alert cooldown）
- **前端**：React 19 + Vite + TypeScript，react-router-dom 做路由
- **基礎設施**：Docker Compose 統一管理 6 個服務，nginx serve 前端靜態檔

## 文件導覽

- [`docs/architecture.md`](docs/architecture.md) — 服務職責、架構圖、資料流說明、技術選型理由
- [`docs/runbook.md`](docs/runbook.md) — OpsBoard 系統本身的維運手冊（常見問題排查）
- [`docs/deployment.md`](docs/deployment.md) — AWS EC2 部署就緒文件（尚未實際部署）
- [`docs/demo-script.md`](docs/demo-script.md) — 逐步 demo 腳本，面試前練習用
- [`docs/api-overview.md`](docs/api-overview.md) — 透過 gateway 呼叫的完整 API 列表
