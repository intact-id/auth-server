# Database migrations (Flyway)

Flyway owns the auth-server schema. Hibernate runs with `ddl-auto: validate`: it checks the entities match
the database at startup and refuses to start if they don't. It never changes the schema.

**To change the schema:** add `V<next number>__<what_it_does>.sql` here, e.g.
`V3__add_company_tax_pin.sql`, and change the entity in the same commit. Flyway runs it once on the next
startup and records it in `flyway_schema_history`.

- Never edit or rename a migration that has been applied anywhere. Write a new one instead.
- Make data changes (seeding, backfills) migrations too, so every environment gets them.
- Prefer `IF NOT EXISTS` / `IF EXISTS`; they make a half-applied manual fix harmless.

**V1__baseline.sql** is the production schema on 2026-11-13 plus reference data (roles, permissions,
service catalog). Production and any database created before Flyway is baselined at version 1
(`baseline-on-migrate`), so V1 is skipped there and only V2 onwards runs. New databases (tests, a new
environment) start from V1.

The dated files in `../migrations/` are the old hand-run scripts, kept for history. Flyway does not read them.
