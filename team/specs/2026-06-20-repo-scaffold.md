# 任務規格：Repo 基礎設施骨架（mysql + redis + docker-compose 骨架）

## 背景/動機

這是「OpsBoard Microservices Demo」專案（Java/Spring Boot 後端工程師作品集）的第一個任務。整個系統最終會由 4 個 app（frontend-dashboard, api-gateway, incident-service, ops-service）+ MySQL + Redis 組成，用 docker-compose 在本機與未來的 AWS EC2 上啟動。

這個任務只負責打地基：建立 repo 的 `.gitignore`，以及一份「目前只含資料庫基礎設施」的 `docker-compose.yml`。之後每個服務完成時，會再回來追加進這個 compose 檔，所以這個檔案從頭到尾都應該保持「可以直接 `docker compose up` 跑得起來」的狀態，不要先放未完成的服務佔位。

## 具體需求

### 1. 根目錄 `.gitignore`

需涵蓋：
- Java/Maven 產物：`target/`、`*.class`、`*.jar`（但不要排除 `*.jar` 如果是 wrapper 用的 `.mvn/wrapper/maven-wrapper.jar`，若有疑慮可用 `target/*.jar` 這種較窄的規則）
- Node/前端產物：`node_modules/`、`dist/`、`.vite/`
- IDE 檔案：`.idea/`、`*.iml`、`.vscode/`（若要保留 `.vscode/launch.json` 之類的設定可自行斟酌，但預設先全部忽略）
- OS 雜檔：`.DS_Store`、`Thumbs.db`
- 環境變數檔：`.env`（但保留 `.env.example` 不忽略）

### 2. 根目錄 `docker-compose.yml`

只放兩個服務：`mysql` 與 `redis`。**之後的任務會修改這個檔案加入其他服務，所以現在的設計要確保新增服務時不需要大改現有結構**（用標準 compose v2 語法，services 用合理命名）。

**mysql 服務**：
- image: `mysql:8.0`
- container_name: `opsboard-mysql`
- 環境變數（用 `${VAR:-預設值}` 語法，讓使用者可選擇性覆寫，預設值僅供本機開發用）：
  - `MYSQL_ROOT_PASSWORD`，預設 `rootpassword`
  - `MYSQL_DATABASE`，預設 `incidentdb`
  - `MYSQL_USER`，預設 `opsboard`
  - `MYSQL_PASSWORD`，預設 `opsboard_pw`
- port mapping：`3306:3306`
- 使用 named volume（例如 `mysql_data`）掛載 `/var/lib/mysql`，確保資料持久化
- healthcheck：用 `mysqladmin ping -h localhost -u root -p$$MYSQL_ROOT_PASSWORD`（注意 compose 裡 `$` 要寫成 `$$` 避免被 compose 自己的變數展開吃掉）這類指令確認 MySQL 真的就緒，不是只有 process 起來

**redis 服務**：
- image: `redis:7-alpine`
- container_name: `opsboard-redis`
- port mapping：`6379:6379`
- healthcheck：`redis-cli ping` 預期回 `PONG`

**頂層 `volumes:`**：宣告 `mysql_data` 這個 named volume。

### 3. 根目錄 `.env.example`

把上面 mysql 的 4 個環境變數列出來（變數名 = 範例值），方便之後使用者複製成 `.env` 自訂。這份檔案要被 git 追蹤（不可被 `.gitignore` 忽略）。

## 驗收標準

1. `docker compose config` 能成功解析 `docker-compose.yml`，不報錯。
2. `docker compose up -d mysql redis`，等待數秒後 `docker compose ps` 顯示兩個服務狀態都是 `healthy`（不是只是 `running`）。
3. 用 MySQL client 連線（例如 `docker exec -it opsboard-mysql mysql -u opsboard -popsboard_pw incidentdb -e "SHOW TABLES;"`）能成功連上 `incidentdb` 這個資料庫且不報認證錯誤。
4. `docker exec -it opsboard-redis redis-cli ping` 回傳 `PONG`。
5. `git status` 在專案根目錄建立一個 `target/` 或 `node_modules/` 測試目錄後，確認這些目錄不會被 git 追蹤（驗證完畢後記得刪除測試用的目錄，不要留在 repo 裡）。
6. `docker compose down -v` 能正常移除容器與 volume，不留殘留資源（這是清理驗證，確保之後重跑不會卡到舊資料）。

## 排除範圍

- 不建立 `frontend-dashboard/`、`api-gateway/`、`incident-service/`、`ops-service/` 這 4 個應用目錄或任何程式碼骨架，這些由後續各自的任務負責。
- 不寫任何 Java 或前端程式碼。
- 不寫 README.md 或其他文件（文件是最後一個階段的任務）。
- 不需要設定 CI/CD。
