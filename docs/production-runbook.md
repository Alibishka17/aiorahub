# Production runbook

## Runtime

- Domain: `aiorahub.com` and `www.aiorahub.com`.
- Origin: DigitalOcean Droplet `161.35.192.132`.
- OS: Ubuntu.
- Reverse proxy: Nginx on ports 80 and 443.
- Application: `aiorahub.service`, Spring Boot under the `aiorahub` system user on `127.0.0.1:8080`.
- Source checkout: `/opt/aiorahub`.
- Artifact: `/opt/aiorahub/target/truehire-0.0.1-SNAPSHOT.jar`.
- Database: PostgreSQL database and role `aiorahub`, listening on localhost only.
- Runtime secrets: `/etc/aiorahub/aiorahub.env`, owned by `root:aiorahub`, mode `0640`.
- DNS: Cloudflare authoritative nameservers.

Flyway owns the application schema. Spring Session initializes its standard JDBC tables and keeps authenticated sessions in PostgreSQL for 24 hours. Hibernate validates application entities at startup and must not use `create` or `create-drop` in production. Application startup never seeds users, vacancies, applications, or interview results.

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
sudo -u postgres pg_dump -Fc aiorahub > /opt/aiorahub-backups/aiorahub-$(date -u +%Y%m%dT%H%M%SZ).dump
sudo tar -C /var/lib/aiorahub -czf /opt/aiorahub-backups/uploads-$(date -u +%Y%m%dT%H%M%SZ).tgz uploads
mvn clean package
sudo systemctl restart aiorahub
sudo systemctl is-active aiorahub
```

JAR, database and uploads backups must run before a production rebuild. Database and CV archives contain personal data and must remain root-readable only (`chmod 0600`).

## PostgreSQL and service identity

The production environment file must contain `SPRING_PROFILES_ACTIVE=prod`, database credentials, `ADMIN_USERNAME`, a BCrypt-only `ADMIN_PASSWORD_HASH`, `HRME_BASE_URL`, `HRME_SERVICE_TOKEN`, and `UPLOAD_DIR=/var/lib/aiorahub/uploads`; use `deploy/systemd/aiorahub.env.example` as the key list. The same random service token must be installed in HRme as `AIORAHUB_SERVICE_TOKEN`. Never commit plaintext passwords or real tokens.

Before the first release with CV uploads:

```bash
sudo install -d -o aiorahub -g aiorahub -m 0750 /var/lib/aiorahub/uploads
```

The installed unit must match `deploy/systemd/aiorahub.service`. After changing it:

```bash
sudo cp deploy/systemd/aiorahub.service /etc/systemd/system/aiorahub.service
sudo systemctl daemon-reload
sudo systemctl restart aiorahub
sudo systemctl show aiorahub -p User -p Group -p NoNewPrivileges
sudo systemd-analyze security aiorahub.service --no-pager
```

The PostgreSQL port must not be publicly reachable. Verify `ss -ltnp` shows port `5432` only on loopback and that the DigitalOcean firewall does not expose it.

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
sudo -u postgres psql -d aiorahub -Atc 'select count(*) from users;'
curl -fsSI https://aiorahub.com/
curl -fsSI https://aiorahub.com/vacancies
curl -fsSI https://aiorahub.com/admin/login
curl -sS -o /dev/null -w '%{http_code}\n' https://aiorahub.com/api/integrations/hrme/catalog
curl -fsSI https://www.aiorahub.com/
journalctl -u aiorahub -n 100 --no-pager
```

Expected public behavior:

- HTTP redirects to `https://aiorahub.com`.
- `https://aiorahub.com` returns `200`.
- `https://aiorahub.com/vacancies` returns `200` and lists only published vacancies.
- `/admin/login` returns `200`; protected admin actions require its session and CSRF token.
- an authenticated `SESSION` cookie has a 24-hour `Max-Age`, and its row remains in `SPRING_SESSION` after an application restart.
- integration endpoints return `401` without the shared Bearer token.
- Flyway reports schema version 4 or later and the service log has no migration, callback or template errors.
- `https://www.aiorahub.com` redirects to the apex domain.
- `/h2-console` is not exposed through Nginx.
- `Accept-Language: ru`, `kk` or `en` selects the corresponding interface until the user stores a manual choice in `AIORAHUB_LANG`.

## Rollback

Restore the previous JAR from `/opt/aiorahub-backups`, restart `aiorahub.service`, and verify the local endpoint before checking the public domain. For a database rollback, stop the application, create a safety dump of the current database, recreate the target database, restore with `pg_restore`, restore the matching uploads archive, and then restart the service. Restore the timestamped Nginx backup from `/root/aiorahub-backups` if the proxy configuration caused the incident.
