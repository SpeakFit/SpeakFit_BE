# SpeakFit Backend HTTPS Deployment

Vercel frontend is served over HTTPS:

```text
https://speak-fit-fe.vercel.app
```

The browser blocks calls from that HTTPS page to an HTTP API such as:

```text
http://<EC2_PUBLIC_IP>
```

This is Mixed Content blocking. The request is blocked in the browser before it reaches Spring Boot, so changing only CORS or opening another Spring port does not fix it.

## Recommended Production Setup

Use a DNS name for the backend and terminate HTTPS before Spring Boot.

```text
Browser
  -> https://api.speakfit.org:443
  -> Nginx on EC2
  -> http://127.0.0.1:8080
  -> Spring Boot
```

Spring Boot can continue to run on port `8080`. Port `443` should be handled by Nginx or an AWS ALB.

## EC2 + Nginx + Certbot

1. Point a backend DNS record to the EC2 public IP.

```text
api.speakfit.org A <EC2_PUBLIC_IP>
```

2. Open EC2 security group inbound rules.

```text
80/tcp from 0.0.0.0/0
443/tcp from 0.0.0.0/0
8080/tcp only from trusted admin IP, or close it if Nginx is on the same EC2
```

3. Install Nginx and Certbot on EC2.

```bash
sudo apt update
sudo apt install -y nginx certbot python3-certbot-nginx
```

4. Copy `config/nginx/speakfit-api.conf` to Nginx.

```bash
sudo cp config/nginx/speakfit-api.conf /etc/nginx/sites-available/speakfit-api
sudo ln -s /etc/nginx/sites-available/speakfit-api /etc/nginx/sites-enabled/speakfit-api
sudo nginx -t
sudo systemctl reload nginx
```

5. Issue a Let's Encrypt certificate.

```bash
sudo certbot --nginx -d api.speakfit.org
```

6. Run the backend with production CORS.

```bash
CORS_ALLOWED_ORIGINS=https://speak-fit-fe.vercel.app,http://localhost:5173
CORS_ALLOWED_ORIGIN_PATTERNS=https://*.vercel.app
SPRING_PROFILES_ACTIVE=prod
```

7. Change the frontend Vercel environment variable and redeploy.

```text
VITE_API_BASE_URL=https://api.speakfit.org
```

## AWS ALB + ACM Alternative

If using AWS managed TLS:

```text
Browser
  -> https://api.speakfit.org
  -> ALB 443 with ACM certificate
  -> EC2 target group 8080
  -> Spring Boot
```

The frontend value is the same:

```text
VITE_API_BASE_URL=https://api.speakfit.org
```

## Temporary Option Without Buying a Domain

Let's Encrypt and ACM generally require a DNS name, not a raw public IP. Until a backend domain exists, use a same-origin Vercel proxy from the frontend:

```json
{
  "rewrites": [
    {
      "source": "/backend/:path*",
      "destination": "http://<EC2_PUBLIC_IP>/:path*"
    }
  ]
}
```

Then set the frontend API base URL to:

```text
VITE_API_BASE_URL=/backend
```

The browser will call:

```text
https://speak-fit-fe.vercel.app/backend/auth/login
```

Vercel proxies that request to:

```text
http://<EC2_PUBLIC_IP>/auth/login
```

This avoids browser Mixed Content because the browser only talks to Vercel over HTTPS. It is a temporary workaround because traffic from Vercel to EC2 is still HTTP.

If using this proxy, add the Vercel frontend origin to backend CORS:

```bash
CORS_ALLOWED_ORIGINS=https://speak-fit-fe.vercel.app,http://localhost:5173
CORS_ALLOWED_ORIGIN_PATTERNS=https://*.vercel.app
```

For production, prefer a real backend HTTPS endpoint.
