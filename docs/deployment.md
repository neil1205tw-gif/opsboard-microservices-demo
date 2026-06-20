# AWS EC2 部署就緒文件

> **聲明：本文件僅說明部署就緒程度與步驟，本專案目前未實際部署到 AWS。** 以下內容沒有實際申請過 AWS 帳號或建立過 EC2 instance，是基於專案現況（`docker-compose.yml`、各服務 Dockerfile）推導出的部署規劃，供之後真正要部署時參考。

## 建議的 EC2 instance 類型

| 項目 | 建議值 |
| --- | --- |
| Instance type | `t3.small`（2 vCPU、2 GiB RAM）或同等級的 burstable instance |
| 作業系統 | Amazon Linux 2023 或 Ubuntu 22.04 LTS |
| 儲存空間 | 至少 20 GiB gp3 EBS（MySQL 資料、Docker image layer 都會佔空間） |

理由：這是一個輕量的 demo 系統，同時跑 6 個容器（MySQL、Redis、3 個 Spring Boot JVM 應用、1 個 nginx）。3 個 Spring Boot 服務 (`incident-service`/`ops-service`/`api-gateway`) 每個 JVM 預設大概各吃數十到一百多 MB heap 起步，加上 MySQL、Redis 常駐記憶體，`t3.small` 的 2 GiB RAM 在沒有真實流量壓力的 demo/面試展示場景下足夠；`t3.micro`（1 GiB）風險較高，3 個 JVM 同時啟動時容易碰到記憶體不足或啟動緩慢。如果之後要加大流量測試或多人同時 demo，可以升級到 `t3.medium`（4 GiB），但目前规模不需要。

## 在 EC2 上安裝 Docker + Docker Compose

以 Amazon Linux 2023 為例：

```bash
# 更新套件索引
sudo dnf update -y

# 安裝 Docker
sudo dnf install -y docker
sudo systemctl enable --now docker

# 把目前使用者加入 docker 群組（避免每次都要 sudo）
sudo usermod -aG docker $USER
newgrp docker

# 安裝 Docker Compose plugin（新版 Docker 用 `docker compose`，而非舊版獨立的 docker-compose 二進位檔）
sudo dnf install -y docker-compose-plugin

# 驗證安裝
docker --version
docker compose version
```

以 Ubuntu 22.04 為例：

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
```

部署應用程式本身：

```bash
git clone <repo-url> opsboard
cd opsboard
cp .env.example .env          # 再依下方「環境變數」章節覆寫密碼
cp frontend-dashboard/.env.example frontend-dashboard/.env
# 視需要編輯 .env，覆寫預設密碼
docker compose up -d --build
```

## 安全群組（Security Group）規劃

需要先決定「要不要在前面加 nginx/反向代理 + HTTPS」：

### 方案 A：直接開放應用 port（最簡單，適合短期 demo）

| Port | 用途 | 來源建議 |
| --- | --- | --- |
| 22 | SSH 管理 | 限制成自己的 IP（例如 `<your-ip>/32`），不要開 `0.0.0.0/0` |
| 5173 | frontend-dashboard | 視需求開放（demo 用可以先限制成特定 IP，正式對外展示再開 `0.0.0.0/0`） |
| 8080 | api-gateway | 同上 |

3306（MySQL）、6379（Redis）、8081（incident-service）、8082（ops-service）**不需要、也不應該**對外開放——`docker-compose.yml` 裡這些 port 目前綁定在 `127.0.0.1`，即使 EC2 的安全群組開了對應 port，外部仍連不進去，維持現狀即可，不需要改動。

**但要注意：repo 裡現有的 `docker-compose.yml` 把 `frontend-dashboard`（5173）跟 `api-gateway`（8080）這兩個服務也綁定在 `127.0.0.1`**（`127.0.0.1:5173:80`、`127.0.0.1:8080:8080`），這是給本機開發用的設定。如果照上面這張表在安全群組開了 5173/8080，但沒有同時調整這兩個服務的 port mapping，**安全群組規則不會生效、外部仍然連不進去**，因為 `127.0.0.1` 只綁定 loopback 介面，安全群組管的是網卡對外流量，兩者是不同層次的限制。要讓方案 A 真的能用，部署前必須把 `docker-compose.yml` 裡這兩個服務的 port mapping 改成：

```yaml
  api-gateway:
    ports:
      - "8080:8080"      # 原為 "127.0.0.1:8080:8080"

  frontend-dashboard:
    ports:
      - "5173:80"         # 原為 "127.0.0.1:5173:80"
```

去掉 `127.0.0.1:` 前綴後，容器才會監聽所有網路介面（`0.0.0.0`），外部流量才能透過 EC2 的安全群組規則打進來。改完要重新 `docker compose up -d`（或 `--build`）讓新的 port mapping 生效。**因為這樣會讓服務暴露到所有介面，請務必更謹慎地設定安全群組的來源範圍**——不要把 5173/8080 的來源開成 `0.0.0.0/0` 對全世界開放，建議限制成自己的 IP（`<your-ip>/32`）或公司 IP range，只有在確定要公開展示的當下才考慮放寬。

這個方案的缺點：使用者要記兩個不同的 port（`:5173` 看畫面、API 走 `:8080`），且沒有 HTTPS，僅適合臨時、信任網路下的展示用途（例如面試現場用視訊分享畫面，或只給特定 IP 存取）。

### 方案 B：前面加 nginx/反向代理，只開 80/443（較完整，適合正式對外）

| Port | 用途 | 來源建議 |
| --- | --- | --- |
| 22 | SSH 管理 | 限制成自己的 IP |
| 80 | HTTP（建議導轉到 443） | `0.0.0.0/0` |
| 443 | HTTPS，反向代理到內部的 5173（前端）與 8080（`/api` 路徑轉給 gateway） | `0.0.0.0/0` |

這個方案需要額外在 EC2 上裝一層 nginx（或用 AWS ALB）做路徑分流（`/` 給前端、`/api/*` 給 gateway），並搭配憑證（例如 Let's Encrypt 或 ACM）。本專案目前的 `frontend-dashboard` 容器本身已經是 nginx serve 靜態檔，但沒有反向代理 `/api` 流量到 gateway 的設定，方案 B 需要額外加一層，**這部分目前程式碼/設定都還沒做，屬於本文件規劃範圍，不在現有系統內**。

對於求職作品集 demo 的使用情境，**方案 A 已經足夠**；方案 B 留給之後若要長期對外公開展示時再評估。

## 環境變數 / 密碼類設定在正式環境的處理方式

`docker-compose.yml` 透過 `${VAR:-default}` 語法讀取環境變數，預設值來自 repo 裡的 `.env.example`（`MYSQL_ROOT_PASSWORD=rootpassword`、`MYSQL_DATABASE=incidentdb`、`MYSQL_USER=opsboard`、`MYSQL_PASSWORD=opsboard_pw`）。**這些預設密碼只能用在本機開發，正式環境一定要覆寫**，做法：

1. 在 EC2 上把 `.env.example` 複製成 `.env`（`docker compose` 會自動讀取同目錄下的 `.env` 檔），並改成正式環境用的密碼：
   ```bash
   cp .env.example .env
   # 編輯 .env，至少改掉 MYSQL_ROOT_PASSWORD、MYSQL_PASSWORD
   ```
2. `.env` 檔不要 commit 進 git（repo 裡只保留 `.env.example` 作為範本，`.env` 應加進 `.gitignore`）。
3. 更進一步的做法（本專案目前規模不需要，但部署到真正會接觸外部流量的環境時建議考慮）：改用 AWS Secrets Manager 或 SSM Parameter Store 存密碼，部署時用啟動腳本把密碼注入成環境變數，而不是把密碼明文放在 EC2 上的 `.env` 檔案裡。
4. **前端對外 API URL：改 `frontend-dashboard/.env` 沒有用，必須改 `docker-compose.yml` 裡的 build arg。** `VITE_API_BASE_URL` 雖然在 `frontend-dashboard/.env.example` 裡有預設值，但實際生效的來源是 `docker-compose.yml` 裡 `frontend-dashboard.build.args.VITE_API_BASE_URL`（目前寫死成 `http://localhost:8080`），`frontend-dashboard/Dockerfile` 是用這個 build arg（`ARG VITE_API_BASE_URL`）在 build time 把值固化進編譯出來的 JS bundle，並不會在容器啟動時去讀 `.env` 檔案。換句話說，**只改 `frontend-dashboard/.env` 完全不會影響到實際跑起來的前端**。正確做法是：

   ```yaml
     frontend-dashboard:
       build:
         context: ./frontend-dashboard
         args:
           VITE_API_BASE_URL: http://<EC2 public IP>:8080   # 原為 http://localhost:8080
   ```

   把 `<EC2 public IP>` 換成 EC2 的 public IP 或網域名稱，改完之後要重新 build image 才會生效：

   ```bash
   docker compose build frontend-dashboard
   docker compose up -d frontend-dashboard
   # 或一次到位：
   docker compose up -d --build
   ```

   如果之後 EC2 的 public IP 變了（例如重啟 instance、沒有用 Elastic IP），要記得回來改這個值並重新 build，否則前端會繼續打舊的位址。

5. **CORS：`api-gateway` 預設只允許 `http://localhost:5173`，部署到 EC2 後要覆寫 `FRONTEND_ORIGIN`。** `api-gateway` 的 CORS 設定預設只允許來源 `http://localhost:5173`，`docker-compose.yml` 目前沒有覆寫這個環境變數。前端部署到 EC2 後，瀏覽器送出的 Origin 會是 EC2 的 public IP/網域（例如 `http://<EC2 public IP>:5173`），跟預設的 `http://localhost:5173` 不一致，瀏覽器直接打 gateway 的 8080 port 時會被 CORS 擋掉。要解決，需要在 `docker-compose.yml` 的 `api-gateway` 服務加上對應的環境變數：

   ```yaml
     api-gateway:
       environment:
         INCIDENT_SERVICE_BASE_URL: http://incident-service:8081
         OPS_SERVICE_BASE_URL: http://ops-service:8082
         FRONTEND_ORIGIN: http://<EC2 public IP>:5173   # 新增，需與步驟 4 的 EC2 位址一致
   ```

   這個值要跟步驟 4 設定的前端對外位址（同一個 IP/網域、同一個 port 5173）保持一致，否則前端雖然能打到 gateway，瀏覽器仍會因 CORS 不符而擋下回應。改完後重新 `docker compose up -d`（`api-gateway` 不需要重新 build，因為這是執行期環境變數，不是 build arg）。

## 尚未做、本文件範圍之外的事項

- 沒有設定 CI/CD（依 spec 排除範圍）。
- 沒有設定 HTTPS/憑證、沒有設定 ALB、沒有設定自動擴展（這些都屬於方案 B 的延伸工作，目前是 demo 系統不需要）。
- 沒有設定資料庫備份策略（demo 用的 MySQL 資料遺失可以直接 `docker compose down -v` 重新 seed，正式環境若需要保留資料則要額外規劃 EBS snapshot 或 mysqldump 排程）。
