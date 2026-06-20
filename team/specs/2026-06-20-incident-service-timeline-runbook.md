# 任務規格：incident-service timeline + runbook + seed data

## 背景/動機

這是「OpsBoard Microservices Demo」的第三個任務，延續上一個任務（`team/specs/2026-06-20-incident-service-core.md`）已經完成的 incident 核心 CRUD（`Incident` entity、建立/查詢/狀態更新 endpoint）。

這次要幫 `incident-service` 補上兩塊功能：
1. **Timeline**：每次 incident 建立或狀態變更時，自動寫一筆歷史記錄，之後可以查詢某個 incident 的完整變更歷程。
2. **Runbook**：每個 monitored service 對應的維運手冊（怎麼處理這個服務出問題時的步驟），先用種子資料 seed 好，之後 ops-service 跟前端會用到。

**這次完成後，incident-service 的 P0 範圍就全部做完了。**

## 具體需求

### 1. `IncidentTimelineEntry` entity

欄位：
- `id`：Long，自動產生主鍵
- `incidentId`：Long，必填，對應 `Incident.id`（用一般的外鍵欄位即可，不一定要建立 JPA `@ManyToOne` 關聯，簡單用 `Long incidentId` 欄位 + DB 層 foreign key 即可，避免過度抽象化）
- `fromStatus`：enum（`IncidentStatus`），**可為 null**（incident 剛建立時的那筆記錄，`fromStatus` 是 null）
- `toStatus`：enum（`IncidentStatus`），必填
- `note`：String，可為 null（這次不需要讓使用者填寫 note，由系統自動產生記錄時可以留空，或者填一句固定格式的說明文字，例如 `"Incident created"` / `"Status changed to INVESTIGATING"`，細節由你決定，不影響驗收）
- `createdAt`：Instant，server 端設定

### 2. 寫入時機（修改既有的 `IncidentService`）

- `createIncident()`：成功建立 incident 後，**在同一個 transaction 裡**寫一筆 timeline entry：`fromStatus = null`、`toStatus = OPEN`。
- `updateStatus()`：成功變更狀態後，**在同一個 transaction 裡**寫一筆 timeline entry：`fromStatus = 變更前的狀態`、`toStatus = 變更後的狀態`。
- 如果狀態轉換不合法（回 409 的情況），**不要**寫入 timeline。

### 3. Timeline 查詢 endpoint

`GET /incidents/{id}/timeline`
- 找不到該 incident → `404`（錯誤格式跟現有的一致）
- 找到的話 → `200`，body 是該 incident 的所有 timeline entry 陣列，**依 `createdAt` 由舊到新排序**（跟 incident 列表「新到舊」的排序方向相反，因為 timeline 是要呈現歷史演進過程）。每筆物件至少包含 `id`、`fromStatus`、`toStatus`、`note`、`createdAt`。

### 4. `Runbook` entity

欄位：
- `id`：Long，自動產生主鍵
- `serviceName`：String，必填（對應 monitored service 的名稱）
- `title`：String，必填
- `content`：String（用 `@Column(length = 4000)` 或 `@Lob`，存放純文字的處理步驟，不需要支援 markdown 渲染）

### 5. Runbook 查詢 endpoint

`GET /runbooks?serviceName={name}`
- `serviceName` 是 query parameter，必填
- 回 `200`，body 是符合該 `serviceName` 的 runbook 陣列（即使預期通常只有一筆，也回陣列，保持 API 形狀一致）
- 找不到對應資料時回 `200` + 空陣列 `[]`，不是錯誤
- `serviceName` 完全沒帶（缺少 query parameter）→ `400`

### 6. Seed 資料

啟動時（用 `CommandLineRunner` 或等效機制）自動 seed 3 筆 runbook，**服務名稱必須精準寫死為以下 3 個**（這是跨任務的硬性協議，下一個任務 `ops-service` 會用一模一樣的 3 個名稱，不可以自己改名或增減）：

1. `serviceName: "payment-api"`，title 跟 content 自訂但要像真實的維運手冊（例如：檢查 payment gateway 連線、檢查交易佇列堆積、重啟服務的步驟等，4-6 個步驟）
2. `serviceName: "checkout-api"`，同上風格
3. `serviceName: "notification-worker"`，同上風格

**Seed 邏輯必須 idempotent**：每次應用程式啟動時都要檢查，如果該 `serviceName` 已經有 runbook 資料就不要重複插入（例如先查詢 `serviceName` 是否已存在，不存在才 insert），確保容器重啟 N 次，資料庫裡每個 service 永遠只有 1 筆對應的 runbook（除非你自己又手動加了别的）。

## 驗收標準

驗證指令前面記得加：
```
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:Path = "$env:JAVA_HOME\bin;C:\Tools\apache-maven-3.9.16\bin;C:\Program Files\Docker\Docker\resources\bin;$env:Path"
```

1. `mvn -f incident-service clean package` 編譯成功。
2. 啟動服務後（本機或透過 `docker compose up -d mysql incident-service`），不需要手動呼叫任何 API，直接查 `GET /runbooks?serviceName=payment-api`、`?serviceName=checkout-api`、`?serviceName=notification-worker` 三個都各回 `200` + 非空陣列（陣列長度為 1），且 `content` 有實質內容（不是空字串或 placeholder）。
3. `GET /runbooks?serviceName=不存在的服務名稱` → `200` + `[]`。
4. `GET /runbooks`（沒帶 `serviceName`）→ `400`。
5. 完整流程驗證：
   - `POST /incidents` 建立一筆 incident（複用上一個任務的方式）→ 確認回應正常後，呼叫 `GET /incidents/{id}/timeline` → `200`，陣列裡有 1 筆，`fromStatus` 是 `null`，`toStatus` 是 `OPEN`。
   - 對同一筆 incident 呼叫 `PUT /incidents/{id}/status` 把狀態改成 `INVESTIGATING` → 成功後再查 `GET /incidents/{id}/timeline` → `200`，陣列現在有 2 筆，依時間舊到新排序，第 2 筆 `fromStatus = "OPEN"`、`toStatus = "INVESTIGATING"`。
   - 再把狀態改成 `MITIGATED` → timeline 變成 3 筆，第 3 筆 `fromStatus = "INVESTIGATING"`、`toStatus = "MITIGATED"`。
   - 嘗試一次不合法的狀態轉換（預期回 409）→ 確認 timeline 筆數沒有增加（還是 3 筆）。
6. `GET /incidents/999999/timeline`（不存在的 incident id）→ `404`。
7. 重啟容器測試 seed idempotent：`docker compose restart incident-service`（或重新跑一次本機程式），重啟後查 `GET /runbooks?serviceName=payment-api`，陣列長度仍然是 1（不是 2 或更多）。
8. `docker compose down -v` 清理乾淨。

## 排除範圍

- 不做 runbook 的新增/編輯/刪除 API（這次只需要 seed + 查詢）。
- 不做 timeline 的分頁。
- 不允許使用者透過 API 自訂 timeline 的 `note` 文字內容（這次系統自動產生即可，不需要額外的輸入欄位或 endpoint）。
- 不動 `frontend-dashboard/`、`api-gateway/`、`ops-service/`，這些是其他任務的範圍。
- 不寫文件。
