# CalcVault Signaling Server — Deployment Guide

## What it does
Handles ONLY the WebRTC SDP/ICE exchange handshake (under 1KB per session).
After P2P connection is established, ALL data flows directly device-to-device.
This server NEVER sees message content or encrypted media.

---

## Deploy on a $5/month VPS (Ubuntu 22.04)

### 1. Get a server
- DigitalOcean Droplet: $5/month, Ubuntu 22.04
- Hetzner CX11: €4/month (cheaper in EU)
- Vultr: $6/month

### 2. Point a domain
Get a domain (or use a free subdomain from afraid.org).
Point an A record to your VPS IP:
```
A   signal.yourdomain.com   →   YOUR_VPS_IP
```

### 3. Install on server
```bash
# SSH into your VPS
ssh root@YOUR_VPS_IP

# Install Node.js 20
curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
apt-get install -y nodejs

# Install nginx + certbot
apt install -y nginx certbot python3-certbot-nginx

# Clone/upload your signaling-server folder
mkdir /opt/calcvault
# Upload via scp:
# scp -r ./signaling-server root@YOUR_VPS_IP:/opt/calcvault/

cd /opt/calcvault/signaling-server
npm install
```

### 4. Configure nginx with SSL
```bash
# /etc/nginx/sites-available/calcvault
server {
    listen 80;
    server_name signal.yourdomain.com;

    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_cache_bypass $http_upgrade;
    }
}

# Enable
ln -s /etc/nginx/sites-available/calcvault /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx

# Get SSL cert (free)
certbot --nginx -d signal.yourdomain.com
```

### 5. Run as system service
```bash
# /etc/systemd/system/calcvault-signal.service
[Unit]
Description=CalcVault Signaling Server
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/calcvault/signaling-server
ExecStart=/usr/bin/node server.js
Restart=on-failure
RestartSec=5
Environment=PORT=3000

[Install]
WantedBy=multi-user.target

# Enable + start
systemctl enable calcvault-signal
systemctl start  calcvault-signal
systemctl status calcvault-signal
```

### 6. Update CalcVault app
In `MainVaultActivity.kt`, replace:
```kotlin
networkEngine.initialize("USER_A", "USER_B", "https://your-signaling-server.com")
```
with:
```kotlin
networkEngine.initialize("USER_A", "USER_B", "https://signal.yourdomain.com")
```

---

## API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | /ping | Health check |
| POST | /signal | Send SDP offer/answer/ICE |
| GET | /poll/:userId | Receive pending signals |
| POST | /relay | Encrypted blob fallback |
| GET | /heartbeat/:userId | Presence update |

---

## Security Notes
- Server stores signals for 60 seconds max, then auto-purges
- No database, no logs, no user accounts
- Rate limited: 100 requests/minute per IP
- All CalcVault payloads are encrypted before reaching server
- Server never sees plaintext content
- Run behind nginx with HTTPS — never HTTP

---

## Firewall
```bash
ufw allow 22    # SSH
ufw allow 80    # HTTP (redirect to HTTPS)
ufw allow 443   # HTTPS
ufw deny 3000   # Block direct Node port — nginx only
ufw enable
```
