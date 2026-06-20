# Demo 腳本

這份文件是給老闆面試前練習用的逐步腳本，目標是 3-5 分鐘內講完整個 demo。每一步都包含「要做的動作」與「預期看到的畫面」，已實際操作過一次驗證內容正確。

## 開始前

1. 確保 Docker Desktop 已啟動，並在終端機（PowerShell）執行：
   ```powershell
   $env:Path = "C:\Program Files\Docker\Docker\resources\bin;$env:Path"
   docker compose up -d --build
   ```
2. 等待約 30-60 秒讓所有服務啟動（第一次 build 會更久）。可以用 `docker compose ps` 確認 6 個容器都是 `Up` 狀態，`mysql`、`redis` 顯示 `(healthy)`。
3. 打開瀏覽器，前往 `http://localhost:5173`。

## 步驟 1：開 dashboard

**動作**：在瀏覽器開啟 `http://localhost:5173`。

**預期畫面**：頁面標題顯示「OpsBoard Dashboard」，下方有一個服務卡片區塊，目前因為 incident 列表是空的（如果是剛 `down -v` 重來），「Incidents」區塊顯示「No incidents.」。

## 步驟 2：看到 3 張服務卡片

**動作**：觀察 dashboard 上方的服務卡片區。

**預期畫面**：看到 3 張卡片，分別是 `payment-api`、`checkout-api`、`notification-worker`，每張卡片顯示：
- 服務名稱 + 狀態徽章：`payment-api` 和 `notification-worker` 顯示綠色 `HEALTHY`，`checkout-api` 顯示紅色 `DEGRADED`。
- 三個指標：Latency（毫秒）、Error Rate（%）、CPU（%）。健康服務的數字會明顯較低（latency 約 20-80ms、error rate 0-2%、CPU 10-40%），`checkout-api` 的數字會明顯偏高（latency 約 500-1500ms、error rate 15-35%、CPU 70-95%）。
- 只有 `checkout-api` 卡片下方有「Trigger Alert」按鈕（健康的服務卡片不會顯示這個按鈕）。

## 步驟 3：對 `checkout-api` 按 Trigger Alert

**動作**：點擊 `checkout-api` 卡片上的「Trigger Alert」按鈕。

**預期畫面**：按鈕下方出現一行文字訊息：「Alert triggered, incident #N created」（N 是一個遞增的數字 ID）。

**背後發生的事**（口頭講解用）：前端呼叫 `POST /api/ops/services/checkout-api/trigger-alert`，ops-service 先確認 Redis 裡沒有這個服務的 cooldown 鎖，鎖成功取得後產生一份隨機指標快照，呼叫 incident-service 建立一筆新 incident（severity 固定為 HIGH），incident-service 寫入 MySQL 並回傳。

## 步驟 4：dashboard 列表出現新 incident

**動作**：（這一步是自動發生的，不需要額外動作；如果沒有自動更新可以重新整理頁面）

**預期畫面**：「Incidents」區塊出現剛建立的那筆 incident，顯示標題（例如「checkout-api health alert triggered」）、所屬服務 `checkout-api`、嚴重程度 `HIGH`、狀態徽章顯示 `OPEN`、建立時間。

## 步驟 5：點進去看詳情

**動作**：點擊剛剛那筆 incident 的列表項目。

**預期畫面**：跳轉到 `http://localhost:5173/incidents/{id}` 詳情頁，依序看到：
- 上方「← Back to dashboard」連結。
- 標題、狀態徽章（`OPEN`）。
- 一段描述文字，內容類似「Automated alert triggered for checkout-api. Snapshot metrics: latencyMs=..., errorRatePercent=..., cpuPercent=...」。
- 服務名稱、嚴重程度、建立時間、更新時間。
- 一個「Mark as INVESTIGATING」按鈕。
- Timeline 區塊，目前只有一筆紀錄（「Incident created」）。
- 最下方「Runbook」區塊，顯示 `checkout-api` 對應的應變手冊內容（標題「Checkout API Service Degradation Runbook」，含 6 條編號步驟）。

## 步驟 6：依序把狀態從 OPEN 推進到 RESOLVED

**動作**：依序點擊按鈕三次：
1. 「Mark as INVESTIGATING」
2. 按鈕變成「Mark as MITIGATED」，點擊
3. 按鈕變成「Mark as RESOLVED」，點擊

**預期畫面**：每點擊一次：
- 上方狀態徽章立即更新成對應狀態。
- 按鈕文字變成下一個狀態（直到 RESOLVED 後按鈕消失，改顯示「Incident resolved.」）。
- 「Updated」時間欄位會更新。

## 步驟 7：每次推進 timeline 都自動多一筆記錄

**動作**：（自動發生，觀察 Timeline 區塊即可）

**預期畫面**：Timeline 區塊從 1 筆變成 4 筆紀錄，依序是：
1. （空）→ OPEN，備註「Incident created」
2. OPEN → INVESTIGATING，備註「Status changed to INVESTIGATING」
3. INVESTIGATING → MITIGATED，備註「Status changed to MITIGATED」
4. MITIGATED → RESOLVED，備註「Status changed to RESOLVED」

每筆都有各自的時間戳記，由舊到新排列。

## 步驟 8：詳情頁可以看到該服務對應的 runbook 內容

**動作**：（自動發生，從步驟 5 開始就已經顯示；這裡是讓使用者特別停留說明）

**預期畫面**：頁面最下方「Runbook」區塊持續顯示，內容是針對 `checkout-api` 預先寫好的應變步驟（例如「檢查 checkout-api health endpoint 與最近部署紀錄」「檢查 cart session cache (Redis) 命中率」等 6 條）。可以對著這個區塊講：「這是系統模擬的維運知識庫，serviceName 對應到的 runbook 在 incident 建立時就已經存在資料庫裡，不需要額外設定」。

## 重新從頭跑 demo（重置成乾淨狀態）

如果要在下一次練習/面試前把系統重置成沒有任何 incident 的乾淨狀態（清掉 MySQL 資料、清掉 Redis cooldown 鎖）：

```powershell
$env:Path = "C:\Program Files\Docker\Docker\resources\bin;$env:Path"
docker compose down -v
docker compose up -d --build
```

`down -v` 會連同 named volume（`mysql_data`）一起刪除，重新 `up` 之後 `incident-service` 會用空的資料庫重新啟動，`RunbookSeeder` 會自動把 3 個服務的 runbook 重新 seed 進去（runbook 內容本身不受影響，只有 incident/timeline 資料會清空）。等服務啟動完成（約 30-60 秒）即可重新從步驟 1 開始。
