# 任務規格：frontend-dashboard 首頁

## 背景/動機

這是「OpsBoard Microservices Demo」的第六個任務。後端三個服務（incident-service、ops-service、api-gateway）都已經完成，可以透過 gateway（port 8080）統一存取所有 API。

這次要建立 `frontend-dashboard`——4 個應用的最後一個，提供使用者操作介面。**這次只做首頁（dashboard）**，包含 service health 卡片、incident 列表、trigger alert 操作；incident 詳情頁是下一個任務的範圍。

## 具體需求

### 1. 專案結構

在 repo 根目錄新增 `frontend-dashboard/`，用 Vite 建立 React + TypeScript 專案（`npm create vite@latest -- --template react-ts` 或等效手動建立）。

加入 `react-router-dom`（這次先設定 `/` 這一條路由，對應 `DashboardPage`；下一個任務會加 `/incidents/:id`，這次先把 router 架好就好，不用實作詳情頁）。

建議的檔案結構：
```
frontend-dashboard/
├── src/
│   ├── api/
│   │   └── apiClient.ts
│   ├── components/
│   │   ├── ServiceHealthCard.tsx
│   │   ├── IncidentListItem.tsx
│   │   ├── IncidentStatusBadge.tsx
│   │   └── TriggerAlertButton.tsx
│   ├── pages/
│   │   └── DashboardPage.tsx
│   ├── App.tsx
│   └── main.tsx
├── .env.example
└── Dockerfile
```
（檔名/細部拆分可以依你的判斷微調，上面是建議，不是強制規格。）

### 2. `apiClient.ts`

統一管理所有 API 呼叫，base URL 只能從 `import.meta.env.VITE_API_BASE_URL` 讀取，**所有路徑都要走 `/api/...` 前綴（透過 gateway），不可以出現任何直接連 incident-service（8081）或 ops-service（8082）的網址**。

至少提供以下函式（型別請自己定義對應 interface，對齊後端既有回應格式）：
- `getServicesHealth(): Promise<ServiceHealth[]>` → `GET {base}/api/ops/services/health`
- `getServiceMetrics(serviceName: string): Promise<ServiceMetrics>` → `GET {base}/api/ops/services/{serviceName}/metrics`
- `triggerAlert(serviceName: string): Promise<TriggerAlertResult>` → `POST {base}/api/ops/services/{serviceName}/trigger-alert`
  - 成功（201）回傳建立的 incident 資訊
  - 失敗且狀態碼是 409（cooldown）時，**不要當成例外丟出讓呼叫端用 try/catch 處理意外錯誤**，而是回傳一個有區分的結果物件（例如 `{ status: "cooldown", retryAfterSeconds: number }` vs `{ status: "created", incidentId: number }`），方便呼叫端依結果分支處理畫面
- `getIncidents(): Promise<Incident[]>` → `GET {base}/api/incidents`

### 3. `DashboardPage`

畫面結構（由上到下）：

1. **Service health 區塊**：頁面載入時呼叫 `getServicesHealth()` 拿到 3 筆服務狀態，對每一筆再各自呼叫 `getServiceMetrics(serviceName)` 拿對應的數值，渲染成 3 張 `ServiceHealthCard`，橫向排列（用簡單的 flex/grid 排版即可）。
2. **Incident 列表區塊**：呼叫 `getIncidents()`，把結果渲染成一排 `IncidentListItem`（每筆都用 `react-router-dom` 的 `Link` 包起來，連到 `/incidents/{id}`——這個路由這次還沒有對應頁面，導到那裡目前會是空白/no match，這是預期的，下一個任務會補上）。

#### `ServiceHealthCard`

每張卡片顯示：
- `serviceName`
- 狀態徽章（用 `IncidentStatusBadge` 同款風格的徽章元件，或另外做一個小型的狀態徽章都可以；`HEALTHY` 用綠色系、`DEGRADED` 用紅色/橙色系的視覺區分）
- 3 個數值：`latencyMs`、`errorRatePercent`、`cpuPercent`
- **如果這張卡片的狀態是 `DEGRADED`**，顯示一個 `TriggerAlertButton`（狀態是 `HEALTHY` 的卡片不顯示這個按鈕）

#### `TriggerAlertButton`

點擊行為：
- 呼叫 `apiClient.triggerAlert(serviceName)`
- 如果結果是「成功建立 incident」→ 在按鈕旁邊顯示一行簡短的成功訊息（例如「Alert triggered, incident #12 created」），並重新呼叫 `getIncidents()` 更新下方的 incident 列表（不需要整頁重新載入）
- 如果結果是「cooldown 中」→ 顯示一行訊息告知還要等多少秒（例如「Cooldown active, retry in 47s」），不需要做即時倒數計時器，顯示當下拿到的秒數即可
- 按鈕本身不需要做 loading spinner 等複雜互動，保持簡單

#### `IncidentListItem`

每筆顯示：`title`、`serviceName`、`severity`、狀態徽章（`IncidentStatusBadge`）、`createdAt`（轉成人類可讀格式，例如用 `toLocaleString()` 即可，不需要額外的日期函式庫）

### 4. 視覺風格

走內部 admin/ops tool 風格：簡單的版面、清楚的留白與對齊，不需要任何動畫函式庫、不需要 UI 元件庫（plain CSS 或 CSS module 即可），不要做任何行銷風格的首頁裝飾（沒有 hero banner、沒有產品介紹文字）。

### 5. 環境變數與設定

新增 `frontend-dashboard/.env.example`：
```
VITE_API_BASE_URL=http://localhost:8080
```

**重要**：因為這是 Vite 的靜態建置，`VITE_*` 環境變數是在 `npm run build` 編譯時就固化進產出的 JS 檔案裡，瀏覽器執行階段沒辦法再改。而且因為這段程式碼是在使用者的瀏覽器裡執行（不是在 docker network 裡面），`VITE_API_BASE_URL` **必須是瀏覽器能存取到的位址**，也就是 `http://localhost:8080`（host 對外映射的 gateway port），**不能填 `http://api-gateway:8080`**（那個 container name 只有 docker network 內部能解析，瀏覽器解析不到）。

### 6. Docker

`frontend-dashboard/Dockerfile`：multi-stage build：
- Stage 1：用 Node image（例如 `node:20-alpine`），`ARG VITE_API_BASE_URL=http://localhost:8080`、`ENV VITE_API_BASE_URL=$VITE_API_BASE_URL`，跑 `npm ci && npm run build`，產出 `dist/`
- Stage 2：用 `nginx:alpine`，把 `dist/` 內容複製到 nginx 的靜態檔案目錄，**並加上一個簡單的 nginx 設定讓所有未知路徑都 fallback 回 `index.html`**（這樣之後 `/incidents/123` 這種 client-side route 直接刷新頁面才不會被 nginx 回 404，要支援 SPA 的路由方式），expose port `80`。

修改根目錄 `docker-compose.yml`，新增 `frontend-dashboard` 服務：
- `container_name: opsboard-frontend`
- `build`: 指向 `./frontend-dashboard`，並透過 `args` 把 `VITE_API_BASE_URL` 設成 `http://localhost:8080`（沿用上面講的「瀏覽器可存取位址」原則）
- port mapping：`127.0.0.1:5173:80`
- `depends_on`：`api-gateway`

## 驗收標準

驗證指令前面記得加（如果要跑 docker 指令）：
```
$env:Path = "C:\Program Files\Docker\Docker\resources\bin;$env:Path"
```

1. `cd frontend-dashboard && npm install && npm run build` 成功（本機 Node v24 已經有裝）。
2. 用 `docker compose up -d --build`（全部 6 個服務：mysql、redis、incident-service、ops-service、api-gateway、frontend-dashboard）全部正常啟動，沒有重啟迴圈，沒有 port 衝突。
3. 瀏覽器打開 `http://localhost:5173`：
   - 看到 3 張 service health 卡片，數值是從 ops-service（透過 gateway）拿到的真實資料（不是寫死在前端的假資料）。
   - `checkout-api` 的卡片顯示 `DEGRADED` 狀態與 Trigger Alert 按鈕，另外兩張沒有這個按鈕。
   - 下方看到 incident 列表（如果資料庫是全新的可能是空列表，這是正常的）。
4. 點擊 `checkout-api` 卡片的 Trigger Alert 按鈕：
   - 顯示成功訊息，下方 incident 列表立刻多一筆新 incident（不用手動重新整理頁面）。
   - 立刻再點一次同一個按鈕 → 顯示 cooldown 訊息，沒有產生第二筆 incident（用瀏覽器 devtools 或重新整理頁面確認列表筆數沒有變多）。
5. 打開瀏覽器 devtools 的 Network 分頁，確認所有 API 請求的網址都是 `http://localhost:8080/api/...`，**沒有任何請求直接打到 8081 或 8082**。
6. Console 分頁沒有任何錯誤訊息（載入時、點擊 trigger alert 時都要確認）。
7. `docker compose down -v` 清理乾淨。

## 排除範圍

- 不做 incident 詳情頁（`/incidents/:id` 對應的頁面），下一個任務的範圍。
- 不做登入/帳號系統。
- 不做即時倒數計時器（cooldown 訊息顯示靜態秒數即可）。
- 不做 polling 自動更新（這是 P1 範圍）。
- 不做任何圖表視覺化。
- 不動 `incident-service/`、`ops-service/`、`api-gateway/` 的程式碼。
- 不寫文件。
