# Integrated container build

This image packages the current Java backend and universal web client together. Production acceptance remains governed by the root architecture and acceptance specifications; the default application database remains legacy H2/JPA pending the reviewed Mongo migration.

Build from the repository root:

```powershell
rtk proxy docker build -f college-events/Dockerfile -t cujammu-pulse:local .
```

The build runs locked npm installation, universal type checks and tests, web export, and Maven verification under Java 21. The root `.dockerignore` allows only source and build inputs. Environment files, local databases, uploads, logs and unrelated reference applications stay outside the build context.

The runtime uses UID/GID `10001:10001`, packaged client assets in `/app/web`, and persistent named volumes for data/uploads/logs. Existing bind-mounted directories require an inventoried, verified migration before changing a running installation to these volumes; their contents are preserved locally and are not copied automatically.

`GET /api/health` is minimal process liveness. `GET /api/ready` validates a database connection and returns HTTP 503 on failure, with a bounded safe response. These probes do not certify identity-provider readiness, mail delivery, university policy, device behavior or release acceptance.

Compose configuration can be validated without starting services:

```powershell
rtk proxy docker compose -f college-events/docker-compose.yml --env-file college-events/.env.example config --no-interpolate --quiet
```

Base images are pinned to official registry index digests inspected on 2026-10-10. A successful metadata lookup is provenance evidence only; vulnerability scans, SBOM/signing, actual image build and runtime probe testing remain separate checks.
