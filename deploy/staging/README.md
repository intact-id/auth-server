# Staging on a laptop

`develop` builds `frankmwangi8118/intact-auth:develop`; this stack runs it and Watchtower
redeploys within ~30 s of each push. Production (`main` → `:latest`) is unaffected.

1. Copy production's env files next to this file (they are not in git):
   `scp -r contabo-2:/opt/apps/intact-id/env ./env`
2. Create `.env` here with the staging-only values:
   ```
   STAGING_JWT_SECRET=<a new random 64-char hex string, not production's>
   # optional:
   STAGING_STORAGE_TYPE=local            # or s3 with a separate staging bucket
   STAGING_PUBLIC_URL=https://<tunnel-host>
   STAGING_WIDGET_URL=https://<widget-tunnel-host>
   STAGING_CORS_ORIGINS=https://<widget-tunnel-host>,http://localhost:5173
   ```
3. `docker login` (if the images are private), then `docker compose up -d`.
4. Tables are created on first start (`ddl-auto: update`). The DB is reachable on
   `localhost:5433` (user/password from `env/postgres.env`) for seeding test data.

Email goes to Mailpit (http://localhost:8025), never to real people. The API is on
http://localhost:8080; expose it with Cloudflare Tunnel or ngrok to test the widget on a phone.
