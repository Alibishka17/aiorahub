# Production runbook

## Runtime

- Domain: `aiorahub.com` and `www.aiorahub.com`.
- Origin: DigitalOcean Droplet `161.35.192.132`.
- OS: Ubuntu.
- Reverse proxy: Nginx on ports 80 and 443.
- Application: `aiorahub.service`, Spring Boot on `127.0.0.1:8080`.
- Source checkout: `/opt/aiorahub`.
- Artifact: `/opt/aiorahub/target/truehire-0.0.1-SNAPSHOT.jar`.
- DNS: Cloudflare authoritative nameservers.

The application uses an in-memory H2 database. Restarting `aiorahub.service` deletes all runtime data and recreates demo data.

## DNS

Required Cloudflare records:

| Type | Name | Target | Mode |
| --- | --- | --- | --- |
| A | `@` | `161.35.192.132` | DNS only until Cloudflare-to-origin connectivity is confirmed |
| CNAME | `www` | `aiorahub.com` | DNS only until Cloudflare-to-origin connectivity is confirmed |

Do not change MX or SPF records while working on the website.

Before enabling the orange-cloud proxy, confirm that a DigitalOcean Cloud Firewall allows Cloudflare IPv4 and IPv6 ranges to reach origin ports 80 and 443. A Cloudflare `522` with a healthy direct-origin response indicates that the edge cannot reach the droplet.

## Deploy

Run on the droplet:

```bash
cd /opt/aiorahub
git fetch origin
git pull --ff-only origin main
mvn test
sudo cp target/truehire-0.0.1-SNAPSHOT.jar /opt/aiorahub-backups/truehire-$(date -u +%Y%m%dT%H%M%SZ).jar
mvn clean package
sudo systemctl restart aiorahub
sudo systemctl is-active aiorahub
```

The backup command must run before replacing or rebuilding the active artifact when behavior changes. Because the repository and build output share a directory today, prefer copying the current JAR to `/opt/aiorahub-backups` before `mvn clean package`.

## Nginx and TLS

The tracked virtual host is `deploy/nginx/aiorahub.conf`. Install it as `/etc/nginx/sites-available/aiorahub`, then verify before reload:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

TLS certificates are managed by Certbot and stored under `/etc/letsencrypt/live/aiorahub.com`. Renewal must remain enabled through `certbot.timer`.

## Validation

```bash
systemctl is-active aiorahub nginx
curl -fsS http://127.0.0.1:8080/ >/dev/null
curl -fsSI https://aiorahub.com/
curl -fsSI https://www.aiorahub.com/
journalctl -u aiorahub -n 100 --no-pager
```

Expected public behavior:

- HTTP redirects to `https://aiorahub.com`.
- `https://aiorahub.com` returns `200`.
- `https://www.aiorahub.com` redirects to the apex domain.
- `/h2-console` is not exposed through Nginx.

## Rollback

Restore the previous JAR from `/opt/aiorahub-backups`, restart `aiorahub.service`, and verify the local endpoint before checking the public domain. Restore the timestamped Nginx backup from `/root/aiorahub-backups` if the proxy configuration caused the incident.
