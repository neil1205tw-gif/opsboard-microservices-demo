# 任務規格：frontend incident 詳情頁

## 背景/動機

這是「OpsBoard Microservices Demo」的第七個任務，也是整個產品故事最後一塊拼圖。上一個任務已經完成 dashboard 首頁，incident 列表的每一筆都已經連到 `/incidents/{id}`，但目前那個路由還沒有對應頁面（會顯示空白）。

這次要把這個頁面補上：顯示 incident 詳情、可以推進狀態、看到 timeline 歷史、看到對應的 runbook。**這個任務做完後，整個 P0 範圍的端到端 demo 流程就完整了。**

## 具體需求

### 1. 路由設定

修改 `frontend-dashboard/src/App.tsx`，在既有的 router 裡新增一條路由：`/incidents/:id` → `IncidentDetailPage`。

### 2. `apiClient.ts` 新增函式

延續上一個任務已有的 `apiClient.ts`，新增：
- `getIncidentById(id: string | number): Promise<Incident>` → `GET {base}/api/incidents/{id}`
- `getIncidentTimeline(id: string | number): Promise<TimelineEntry[]>` → `GET {base}/api/incidents/{id}/timeline`
- `getRunbooks(serviceName: string): Promise<Runbook[]>` → `GET {base}/api/runbooks?serviceName={serviceName}`
- `updateIncidentStatus(id: string | number, status: string): Promise<UpdateStatusResult>` → `PUT {base}/api/incidents/{id}/status`
  - 跟 `triggerAlert` 一樣的設計原則：用回傳值區分「成功」跟「409 非法轉換」，不要用 throw 處理 409 這種預期內的業務情境

### 3. `IncidentDetailPage`

從路由參數拿到 `id`，頁面載入時平行呼叫：`getIncidentById(id)`、`getIncidentTimeline(id)`、（拿到 incident 之後）`getRunbooks(incident.serviceName)`。

畫面結構（由上到下）：

1. **返回連結**：一個連回 `/`（dashboard 首頁）的連結，例如「← Back to dashboard」。
2. **Incident 基本資訊**：`title`、`description`、`serviceName`、`severity`、目前狀態徽章、`createdAt`、`updatedAt`。
3. **狀態更新控制**：
   - 狀態推進規則跟後端一致：`OPEN → INVESTIGATING → MITIGATED → RESOLVED`，只能往下一階推進，不能跳級或回退。
   - 畫面上**只顯示「目前狀態的下一個合法狀態」這一個選項**（用一個按鈕即可，例如目前是 `OPEN` 時按鈕文字是「Mark as INVESTIGATING」），不要做成下拉選單列出所有狀態（因為大部分狀態本來就不合法，沒必要讓使用者看到並選到會被拒絕的選項）。
   - 如果目前狀態已經是 `RESOLVED`（沒有下一階了），不顯示任何狀態更新按鈕，可以顯示一行文字說明「Incident resolved」之類的。
   - 點擊按鈕 → 呼叫 `updateIncidentStatus(id, 下一個狀態)` → 成功後重新呼叫 `getIncidentById(id)` 跟 `getIncidentTimeline(id)` 更新畫面（不整頁刷新）→ 如果意外收到 409（理論上 UI 已經限制不會發生，但 API 端可能因為其他原因仍回 409），顯示一行錯誤訊息即可，不需要複雜處理。
4. **Timeline 區塊**：把 `getIncidentTimeline` 的結果依時間順序（後端已經是舊到新排序）渲染成一個列表，每筆顯示 `fromStatus`（如果是 null 顯示成類似「(created)」的文字）、`toStatus`、`createdAt`。
5. **Runbook 區塊**：顯示 `getRunbooks(incident.serviceName)` 的結果，如果有資料，顯示 `title` 跟 `content`（`content` 是純文字，用 `<pre>` 或保留換行的方式顯示即可）；如果該服務沒有對應 runbook（陣列是空的），顯示一行「No runbook available for this service」之類的文字，不要當成錯誤處理。

### 4. 視覺風格

延續上一個任務已經建立的視覺風格（plain CSS、admin 工具風格），新增的元件（例如 `TimelineEntryRow`、`RunbookPanel`）風格要跟既有元件一致，不要引入新的設計語言或函式庫。

## 驗收標準

驗證指令前面記得加（如果要跑 docker 指令）：
```
$env:Path = "C:\Program Files\Docker\Docker\resources\bin;$env:Path"
```

1. `cd frontend-dashboard && npm run build` 成功。
2. `docker compose up -d --build`（全部 6 個服務）正常啟動。
3. 完整端到端流程驗證（瀏覽器或等效的自動化方式驗證實際畫面狀態，不能只驗證 API 回應）：
   - 在 dashboard 首頁對 `checkout-api` 按 Trigger Alert，建立一筆新 incident。
   - 點擊 incident 列表裡剛建立的那一筆 → 導到 `/incidents/{id}`，畫面顯示正確的 `title`、`serviceName`、`severity`、狀態是 `OPEN`。
   - Timeline 區塊顯示 1 筆記錄（`fromStatus` 是空/「(created)」、`toStatus` 是 `OPEN`）。
   - Runbook 區塊顯示 `checkout-api` 對應的 runbook 內容（非空）。
   - 按下狀態推進按鈕（「Mark as INVESTIGATING」）→ 畫面狀態徽章變成 `INVESTIGATING`，timeline 變成 2 筆。
   - 再按一次（「Mark as MITIGATED」）→ 狀態變 `MITIGATED`，timeline 變 3 筆。
   - 再按一次（「Mark as RESOLVED」）→ 狀態變 `RESOLVED`，timeline 變 4 筆，狀態更新按鈕消失（顯示已結案的說明文字）。
   - 點擊「返回」連結，確認能正常回到 dashboard 首頁。
4. 直接在瀏覽器網址列輸入 `http://localhost:5173/incidents/{剛剛那個id}` 重新整理頁面（不是透過前端導航），確認頁面能正常顯示（驗證 nginx 的 SPA fallback 設定有效），不是空白或 404。
5. Devtools Network 分頁確認新增的這些 API 呼叫一樣都打 `http://localhost:8080/api/...`，沒有直連 8081/8082。
6. Console 沒有錯誤訊息。
7. `docker compose down -v` 清理乾淨。

## 排除範圍

- 不做 incident 的 title/description 編輯功能。
- 不做刪除 incident 功能。
- 不允許使用者在 timeline 裡手動加註記（note 文字維持後端自動產生的內容）。
- 不做 runbook 的編輯功能（純顯示）。
- 不動 `incident-service/`、`ops-service/`、`api-gateway/` 的程式碼。
- 不寫文件（下一個任務的範圍）。
