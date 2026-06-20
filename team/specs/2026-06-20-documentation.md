# 任務規格：文件套件

## 背景/動機

這是「OpsBoard Microservices Demo」的第八個任務。前七個任務已經把整套系統做完並逐項驗證通過：`incident-service`（核心 CRUD + timeline + runbook）、`ops-service`（health/metrics/trigger-alert + Redis cooldown）、`api-gateway`（Spring Cloud Gateway 統一路由）、`frontend-dashboard`（dashboard 首頁 + incident 詳情頁），整套系統可以用 `docker compose up` 跑起來，端到端 demo flow 已經驗證可行。

這次要補上六份文件。**這個任務的前提是系統已經真的能跑**，所以文件裡的指令、網址、欄位名稱都要對照實際程式碼/設定檔寫，不要憑空想像或照抄這份 spec 裡的摘要就交卷——請實際去讀 `docker-compose.yml`、各服務的 `application.yml`/`pom.xml`、controller 程式碼來確認細節（例如 env var 預設值、port、endpoint 路徑），最後**請實際照著你寫的文件指令跑一次**確認可以正常執行。

## 系統現況摘要（寫文件前請對照實際程式碼核實，這裡只是給你方向）

- 6 個 docker-compose 服務：`mysql`(3306)、`redis`(6379)、`incident-service`(8081)、`ops-service`(8082)、`api-gateway`(8080)、`frontend-dashboard`(5173，nginx serve 編譯後的 React 靜態檔)。全部 port 都綁定在 `127.0.0.1`。
- 前端只透過 `http://localhost:8080/api/...`（gateway）呼叫後端，不直連 8081/8082。
- 3 個監控服務：`payment-api`（健康）、`checkout-api`（degraded，demo 用來觸發 alert）、`notification-worker`（健康）。
- 產品故事（demo 主流程）：開 dashboard → 看到 3 張服務卡片 → 對 `checkout-api` 按 Trigger Alert → ops-service 走 Redis cooldown 邏輯並呼叫 incident-service 建立 incident → dashboard 列表出現新 incident → 點進去看詳情 → 依序按按鈕把狀態從 OPEN 推進到 INVESTIGATING → MITIGATED → RESOLVED → 每次推進 timeline 都自動多一筆記錄 → 詳情頁可以看到該服務對應的 runbook 內容。

## 具體需求

需要建立以下 6 份文件，**都用繁體中文或英文皆可，但同一份文件內部用語要一致**（不要中英文交雜到讓人看不懂，技術名詞如 API/Docker 等保留原文是正常的）：

### 1. `README.md`（根目錄）

- 專案簡介（一兩段話講清楚這是什麼、為什麼存在——求職作品集，展示後端工程能力）
- 4 個應用 + 2 個基礎設施的架構總覽（簡短，詳細放 `docs/architecture.md`）
- Quick Start：`docker compose up -d --build`，啟動後可以打開哪個網址（`http://localhost:5173`）、有哪些 API base URL 可以測（`http://localhost:8080/api/...`）
- 技術棧清單，明確列出：Java 17、Spring Boot 3、MySQL、Redis、React + Vite + TypeScript、Docker Compose、Spring Cloud Gateway
- 連結到其他 5 份文件（相對路徑連結）

### 2. `docs/architecture.md`

- 4 個服務 + MySQL + Redis 各自的職責說明
- 一張簡單的架構圖，**用 Mermaid 語法**寫在一個 ` ```mermaid ` code block 裡（GitHub 會直接渲染），畫出 frontend → gateway → 兩個後端服務 → MySQL/Redis 的關係
- 主要 demo 流程的資料流說明（對照產品故事 8 步驟，講清楚一次 trigger alert 動作背後實際打了哪些服務、哪些 API）
- 為什麼選 MySQL（incident-service 的持久化資料）、為什麼選 Redis（ops-service 的 alert cooldown，**這是真實用途不是裝飾**）

### 3. `docs/runbook.md`

**注意**：這是「維運 OpsBoard 這個系統本身」的 runbook（troubleshooting guide），跟系統裡 seed 在 incident-service 資料庫裡、給 3 個監控服務用的 runbook（前端詳情頁顯示的那個）是完全不同的兩個東西——**請在文件開頭明確說明這個區別**，避免讀者搞混。

內容至少包含幾個常見問題排查情境（請實際測試過至少 2-3 個再寫，不要憑空編）：
- `incident-service` 啟動失敗 / 連不上 MySQL 怎麼排查
- `ops-service` 連不上 Redis 怎麼排查
- 透過 gateway 打 API 收到非預期回應（例如 404/502）時怎麼排查是哪一段斷掉
- 怎麼用 `docker compose logs <service>` 看各服務的 log

### 4. `docs/deployment.md`

**這份文件只需要寫部署就緒文件，不需要實際申請 AWS 帳號或部署一台真的 EC2**。內容：
- 建議的 EC2 instance 類型/規格（針對這種輕量 demo 系統，例如 t3.small 或同等級，說明理由）
- 在 EC2 上安裝 Docker + Docker Compose 的步驟
- 需要開放的安全群組（Security Group）port：至少要說明 80/443（如果前面加 nginx/反向代理）或直接開 5173（frontend）跟 8080（gateway）的取捨
- 環境變數/密碼類設定在正式環境該怎麼處理（例如不要用 repo 裡 `.env.example` 的預設密碼，要怎麼覆寫）
- 明確註明：「本文件僅說明部署就緒程度與步驟，本專案目前未實際部署到 AWS」這類聲明

### 5. `docs/demo-script.md`

對應產品故事的 8 個步驟，寫成**逐步可以照做的腳本**，每一步包含：
- 要做的動作（點哪裡、打什麼指令）
- 預期看到的畫面/結果（具體到「應該看到 xxx」）

目標是讓使用者（老闆本人）可以對著這份文件練習，在面試現場 3-5 分鐘內講完整個 demo。最後可以加一小段「如果要重新從頭跑 demo（例如重置成沒有 incident 的乾淨狀態），該怎麼做」（提示：`docker compose down -v` 再重新 `up`）。

### 6. `docs/api-overview.md`

一個表格，列出**透過 gateway 呼叫**的所有 endpoint（也就是 `http://localhost:8080/api/...` 這個層級，不要列 incident-service/ops-service 各自原始的 port），至少包含欄位：HTTP method、路徑、簡短說明、request body 範例（如果有）、成功回應的範例、可能的錯誤狀態碼。涵蓋目前系統裡所有現有的 endpoint（incident CRUD、timeline、runbook、ops health/metrics/trigger-alert）。

## 驗收標準

驗證指令前面記得加（如果要跑 docker 指令）：
```
$env:Path = "C:\Program Files\Docker\Docker\resources\bin;$env:Path"
```

1. 六份文件都存在於規格指定的路徑，內容沒有 `TODO`、`TBD`、`(待補)` 之類的佔位文字。
2. 實際照著 `README.md` 的 Quick Start 指令操作一次：`docker compose up -d --build`，確認服務都正常啟動，能打開 `http://localhost:5173` 看到 dashboard。
3. `docs/architecture.md` 裡的 Mermaid 圖表語法要正確（可以用任何線上 Mermaid 預覽工具或 IDE 的 Markdown 預覽確認語法沒有錯誤，不會渲染失敗）。
4. `docs/api-overview.md` 裡列出的每一個 endpoint，請至少用 curl 實際呼叫驗證一次路徑跟回應格式跟文件描述一致（路徑前綴要是 `/api/...`，不是後端服務原始的路徑）。
5. `docs/demo-script.md` 的 8 個步驟，請實際照著操作一次完整流程（用瀏覽器或你之前驗證用過的 headless 方式都可以），確認每一步描述的畫面跟實際看到的一致。
6. 驗證完後 `docker compose down -v` 清理乾淨。

## 排除範圍

- 不實際部署到 AWS EC2（`docs/deployment.md` 只是就緒文件）。
- 不寫 CI/CD 相關文件。
- 不修改任何既有的程式碼或 `docker-compose.yml`（如果驗證過程中發現文件跟實際行為不一致，請以實際行為為準調整文件內容，不要反過來改程式碼去配合文件）。
- 不需要產生圖片檔（架構圖用 Mermaid 文字語法即可，不需要額外的圖片生成工具）。
