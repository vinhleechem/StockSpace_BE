# Production Migration Runbook

## Purpose

Production deployment uses `ops/run-migrations.sh` to apply only SQL files
that are not present in `public.schema_migrations`. The runner stores the
SHA-256 checksum of each applied file and refuses a changed file.

This runbook is for the migration-runner release. It does not authorize
business-data cleanup or manual edits to an already-applied migration.

## Before the first production rollout

1. Freeze merges into `main` while the release is being verified.
2. Take a PostgreSQL backup and perform a restore test outside production.
3. Run the read-only preflight from
   `ops/maintenance/migration_baseline_preflight.sql` against a production
   clone first.
4. Compare the clone schema with
   `ops/maintenance/migration_baseline_manifest.txt`.
5. Only after the historical files are verified, run the baseline script in
   dry-run mode. The manifest intentionally excludes every `20260825_*` file.

## Baseline commands

From the repository root on the deployment host:

```bash
bash ops/maintenance/migration_baseline.sh --docker
```

The command must print `DRY-RUN` and must not connect to or modify the
database. After backup, restore verification and schema review, the explicit
write requires both flags below:

```bash
ALLOW_MIGRATION_BASELINE=true \
  bash ops/maintenance/migration_baseline.sh --docker --apply
```

The command must pass the preflight first. It records only the manifest
filenames and their current checksums; it does not execute their SQL again.

## Normal deployment

`deploy.sh deploy` starts PostgreSQL, waits for readiness, and invokes:

```bash
bash "$APP_DIR/ops/run-migrations.sh" --docker
```

The runner acquires the `stockspace.schema_migrations` advisory lock, checks
all files in lexical filename order, applies pending files with
`ON_ERROR_STOP=1`, records a checksum after success, and releases the lock.
An error stops deployment before the application build/restart completes.

## Required postchecks

```bash
bash ops/run-migrations.sh --docker --dry-run
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  exec -T postgres psql -X -v ON_ERROR_STOP=1 \
  -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-stockspace}" \
  -c 'SELECT filename, applied_at FROM public.schema_migrations ORDER BY filename;'
```

Then verify application health and run the release smoke test. Running the
runner a second time must report only `Already applied` entries and must not
change schema or business data.

## Failure handling

- **Checksum mismatch:** stop. Restore the intended file from the release
  commit or create a new migration; never update the ledger manually to hide
  the mismatch.
- **SQL failure:** inspect the failing filename and database error. Confirm the
  file has no ledger row. Restore from backup only according to the reviewed
  rollback procedure; do not blindly rerun a partially committed business
  operation.
- **Concurrent deployment:** wait for the first runner to finish. The second
  runner must acquire the advisory lock and then become a no-op.
- **Application health failure after migration:** keep the migration record,
  stop repeated deploy attempts, and use the release rollback plan. A code
  rollback does not automatically roll back an applied schema migration.

## Rules for future migrations

- Add a new lexically ordered `.sql` file under `ops/migrations/`.
- Never edit, rename, delete or reorder a file already recorded in
  `schema_migrations`.
- Make new migrations transactional where PostgreSQL permits it and use
  explicit guards for objects that may already exist.
- Test on a disposable database and run the integration script with an
  explicit test database:

```bash
RUN_MIGRATION_DB_TESTS=true PGDATABASE=stockspace_test \
  bash ops/tests/migration_runner_integration.sh
```

- Review backup, lock, checksum and second-run behavior before merging to
  `main`.

## Rollback boundary

The runner prevents accidental replay; it is not a schema rollback system.
Every destructive migration needs a reviewed backup and a separate rollback
procedure before production approval.
