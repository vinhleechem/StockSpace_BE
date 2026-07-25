# 🚀 StockSpace — Hướng dẫn Deploy lên VPS

## 📁 Cấu trúc files deploy

```
StockSpace_BE/
├── Dockerfile              ← Build Spring Boot image
├── docker-compose.yml      ← Orchestrate: app + postgres + nginx
├── .env                    ← Biến môi trường (KHÔNG commit lên git)
├── .env.example            ← Template .env
├── .dockerignore           ← Bỏ qua files không cần khi build
├── deploy.sh               ← Script deploy tự động
├── Makefile                ← Shortcut commands
└── nginx/
    ├── nginx.conf          ← Reverse proxy config
    └── ssl/                ← SSL certificates (nếu dùng HTTPS)
```

---

## 🖥️ Yêu cầu VPS

| Thành phần | Tối thiểu |
|------------|-----------|
| RAM        | 2 GB      |
| CPU        | 1 vCPU    |
| Disk       | 20 GB     |
| OS         | Ubuntu 22.04 LTS |

---

## ⚡ Lần đầu deploy (Setup)

### Bước 1 — SSH vào VPS

```bash
ssh root@103.153.75.143
```

### Bước 2 — Upload deploy script lên VPS

> Từ máy local chạy lệnh này:

```bash
scp deploy.sh root@103.153.75.143:/root/deploy.sh
```

### Bước 3 — Chạy setup (cài Docker + clone repo)

> Trước tiên, mở deploy.sh và thay REPO_URL bằng URL repo GitHub của bạn.

```bash
# Trên VPS
chmod +x /root/deploy.sh
bash /root/deploy.sh setup
```

Script sẽ tự động:
- Cài Docker + Docker Compose
- Clone repo về `/opt/stockspace`
- Tạo file `.env` từ template

### Bước 4 — Điền biến môi trường

```bash
nano /opt/stockspace/.env
```

Các biến **bắt buộc** phải thay:

| Biến | Mô tả |
|------|-------|
| `DB_PASSWORD` | Mật khẩu PostgreSQL (mạnh, random) |
| `JWT_SECRET` | Chuỗi ≥ 64 ký tự random — dùng: `openssl rand -base64 64` |
| `CLOUDINARY_CLOUD_NAME` | Lấy từ cloudinary.com |
| `CLOUDINARY_API_KEY` | Lấy từ cloudinary.com |
| `CLOUDINARY_API_SECRET` | Lấy từ cloudinary.com |
| `MAIL_USERNAME` | Gmail dùng gửi mail |
| `MAIL_PASSWORD` | App Password 16 ký tự |
| `GOOGLE_CLIENT_ID` | Google OAuth Client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth Secret |
| `VNP_RETURN_URL` | `http://103.153.75.143/api/auth/vnpay-callback` |
| `FRONTEND_URL` | URL frontend (Vercel/Netlify/...) |
| `GEMINI_API_KEY` | Lấy từ aistudio.google.com |

### Bước 5 — Deploy!

```bash
bash /opt/stockspace/deploy.sh deploy
```

---

## 🔄 Các lần deploy sau (update code)

```bash
# Trên VPS
bash /opt/stockspace/deploy.sh deploy
```

---

## 📋 Các lệnh thường dùng

```bash
# Xem log realtime
bash deploy.sh logs              # log app
bash deploy.sh logs postgres     # log database
bash deploy.sh logs nginx        # log nginx

# Trạng thái
bash deploy.sh status

# Restart app (không rebuild)
bash deploy.sh restart

# Backup database
bash deploy.sh backup
# → lưu vào /opt/stockspace/backups/stockspace_YYYYMMDD_HHMMSS.sql.gz

# Dừng tất cả
bash deploy.sh stop
```

---

## 🔒 Cài SSL (HTTPS) với Let's Encrypt

### 1. Cài Certbot

```bash
apt-get install -y certbot
```

### 2. Dừng Nginx tạm để lấy cert

```bash
docker compose stop nginx
```

### 3. Lấy certificate

```bash
certbot certonly --standalone -d your-domain.com
```

### 4. Copy cert vào thư mục ssl

```bash
cp /etc/letsencrypt/live/your-domain.com/fullchain.pem /opt/stockspace/nginx/ssl/
cp /etc/letsencrypt/live/your-domain.com/privkey.pem   /opt/stockspace/nginx/ssl/
```

### 5. Mở block HTTPS trong nginx.conf

Trong `nginx/nginx.conf`:
- Bỏ comment block `server { listen 443 ssl ... }`
- Thêm `return 301 https://$host$request_uri;` vào block HTTP

### 6. Khởi động lại Nginx

```bash
docker compose up -d nginx
```

### 7. Tự động renew cert (cron job)

```bash
crontab -e
# Thêm dòng này:
0 3 * * * certbot renew --quiet && docker compose -f /opt/stockspace/docker-compose.yml restart nginx
```

---

## 🛠️ Troubleshooting

### App không khởi động được

```bash
docker compose logs app
```

### Không connect được database

```bash
docker compose exec postgres psql -U postgres -d stockspace -c "\l"
```

### Reset hoàn toàn (XÓA HẾT DATA!)

```bash
docker compose down -v   # ⚠️ Xóa cả volume postgres
docker compose up -d --build
```

### Xem tài nguyên đang dùng

```bash
docker stats
```

---

## 🌐 Kiểm tra sau deploy

| Endpoint | Kết quả mong đợi |
|----------|-----------------|
| `http://103.153.75.143/actuator/health` | `{"status":"UP"}` |
| `http://103.153.75.143/swagger-ui/index.html` | Swagger UI |

---

## 📌 Firewall — Mở port cần thiết

```bash
ufw allow 22     # SSH
ufw allow 80     # HTTP
ufw allow 443    # HTTPS
ufw enable
```

> **Không cần mở port 8080** — Nginx reverse proxy từ 80/443 vào app:8080 nội bộ.
