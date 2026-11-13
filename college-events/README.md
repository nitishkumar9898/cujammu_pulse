# CUJAMMU-PULSE local professor demo

This repository contains a university services platform in active implementation. The isolated `professor-demo` profile is for a local, synthetic walkthrough only. It uses an in-memory H2 database, binds the backend to `127.0.0.1`, disables external email, AI and identity providers, and creates synthetic accounts and records. Demo database contents disappear when the backend stops.

This is not a production deployment guide. The platform has incomplete workflows and has not passed institutional acceptance, target MongoDB replica-set verification, native-device testing, independent security assessment, or production operations review. Do not describe it as production-ready, bug-free, or an official university service.

## Local prerequisites

- Windows PowerShell, Node.js/npm, JDK 21, and Maven 3.9 or newer.
- Keep the checkout on the local machine. No domain or real university data is needed for the professor walkthrough.

From the repository root, build and check the shared web client:

```powershell
rtk proxy powershell -NoProfile -Command "Set-Location cujammu-pulse; npm ci; npm run typecheck; npm test; npm run build:web"
```

Build and test the backend:

```powershell
rtk proxy powershell -NoProfile -Command "mvn -f college-events/pom.xml clean verify"
```

Start the backend from the `college-events` directory. Set a temporary password that satisfies the configured password policy; the same password is used by the synthetic demo accounts:

```powershell
rtk proxy powershell -NoProfile -Command "`$env:PROFESSOR_DEMO_ENABLED='true'; `$env:PROFESSOR_DEMO_PASSWORD='Choose-A-Temporary-Strong-Password-123!'; `$env:SPRING_PROFILES_ACTIVE='professor-demo'; Set-Location college-events; java -jar target/events-1.0.0.jar"
```

Open `http://127.0.0.1:18080/`. The seeded synthetic login names are `student@example.invalid`, `finder@example.invalid`, `faculty@example.invalid`, `reviewer@example.invalid`, `admin@example.invalid`, and `warden@example.invalid`; use the temporary password above. Never reuse a personal or production password.

The web export must exist at `cujammu-pulse/dist` before startup. The profile writes only beneath `college-events/target/professor-demo/`. Its environment guard rejects production profiles, persistent or alternate database configuration, non-loopback binding, external providers, and workspace-level upload/log paths.

## Walkthrough boundaries

Use the walkthrough to review available shared web flows with synthetic records. The following remain unavailable or incomplete and must be presented that way: verified campus/library QR scanning and scoped library reports, event attendance issuance and physical scan verification, approved academic PDFs and timetable publication, production notification delivery, native camera/offline behavior, MongoDB concurrency and recovery, and several complete office workflows.

The synthetic professor demo is evidence for those exact local flows only. It does not prove production authorization, university policy, mobile-device behavior, resilience, load capacity, privacy/legal acceptance, or security. The release matrix at the repository root lists current implementation evidence and outstanding gates.

## Production is a separate release

The bundled Docker image runs with the `prod` profile; `docker compose up` is not the isolated professor demo. Do not expose that deployment or connect production data until the required institutional configuration, privacy and security reviews, target-database migration/restore tests, native and web acceptance, monitoring/on-call ownership, signed release artifacts, and university approval are complete. No test result establishes zero defects or zero vulnerabilities.
