# Staging on a laptop

`develop` builds the `:develop` image of every service (auth, billing, face, OCR, sanctions); this
stack runs them and Watchtower redeploys within ~30 s of each push. Production (`main` → `:latest`)
is unaffected.

1. Copy production's env files next to this file (they are not in git):
   `scp -r contabo-2:/opt/apps/intact-id/env ./env`
2. Create `.env` here with the staging-only values:
   ```
   STAGING_JWT_SECRET=<a new random 64-char hex string, not production's>
   # optional:
   STAGING_STORAGE_TYPE=local            # or s3 with the staging bucket:
   # STAGING_S3_BUCKET=intact-kyc-docs-staging
   # STAGING_S3_ACCESS_KEY=<staging token access key id>
   # STAGING_S3_SECRET_KEY=<staging token secret>
   STAGING_PUBLIC_URL=https://<tunnel-host>
   STAGING_WIDGET_URL=https://<widget-tunnel-host>
   STAGING_CORS_ORIGINS=https://<widget-tunnel-host>,http://localhost:5173
   ```
3. `docker login` (if the images are private), then `docker compose up -d`.
4. auth-server's tables, roles and service catalog are created on first start by Flyway
   (`src/main/resources/db/migration`). The DB is reachable on `localhost:5433` (user/password from
   `env/postgres.env`) for seeding test data.

billing-service does not create its own tables: on a fresh database, apply its schema from the
billing-service repo once:
```
for f in Client BillingProfile Wallet WalletTransaction WalletLock RecurringFee Invoice UsageRecord PerCallPricing ServiceCharge; do
  docker compose exec -T postgres psql -U postgres -d api-billing-service < <billing-repo>/src/main/resources/schema/ddl_$f.sql
done
```
On a database that already has them, apply that repo's `schema/migrations/*.sql` instead.

Email goes to Mailpit (http://localhost:8025), never to real people. The API is on
http://localhost:8080; expose it with Cloudflare Tunnel or ngrok to test the widget on a phone.
