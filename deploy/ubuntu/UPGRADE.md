# Vmq modernization upgrade

The modernized runtime uses Java 17, Spring Boot 3.5 and H2 2.3. H2 2.3 cannot
open the existing H2 1.4 file directly, so the database must be converted once
before the new application image is started.

Do not run this while the `vmq` container is running.

## Build the candidate images

```bash
docker build \
  --build-arg VCS_REF="$(git rev-parse HEAD)" \
  -t vmq:modernized .

docker build \
  -f deploy/ubuntu/h2-migrator.Dockerfile \
  -t vmq-h2-migrator:1.4-to-2.3 .
```

## Convert the named-volume database

Create a host backup directory on the Ubuntu VM, stop only Vmq, and run the
converter against the existing `vmq_data` volume:

```bash
sudo install -d -m 0700 /opt/vmq/backups
docker compose -f deploy/ubuntu/compose.yaml stop vmq

docker run --rm \
  -v vmq_data:/data \
  -v /opt/vmq/backups:/backup \
  vmq-h2-migrator:1.4-to-2.3
```

The converter:

1. copies the original database to a timestamped host backup;
2. exports it with H2 1.4.197;
3. retains the old file inside the volume under a timestamped name;
4. imports a new H2 2.3.232 database;
5. compares row counts for `PAY_ORDER`, `PAY_QRCODE`, `SETTING`, and
   `TMP_PRICE`;
6. restores the old file automatically if any step fails.

Do not start the new image unless the converter reports `H2 conversion
succeeded`.

## Start and verify

```bash
VMQ_IMAGE=vmq:modernized \
  docker compose -f deploy/ubuntu/compose.yaml up -d vmq

docker inspect --format '{{.State.Health.Status}}' vmq
curl --fail --silent --show-error http://127.0.0.1:18082/ >/dev/null
```

After the application is healthy, verify login, dashboard totals, QR-code
upload, order creation/query, and one callback flow before changing the live
gateway configuration.

During the first modernized startup, the schema runner aligns Hibernate 6's
`PAY_ORDER_SEQ` and `PAY_QRCODE_SEQ` above the highest migrated IDs. A
successful post-upgrade order insert is therefore a mandatory acceptance
check, not just a health-check response.
