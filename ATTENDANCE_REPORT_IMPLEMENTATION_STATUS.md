# Attendance report implementation status

Implemented:

- Event-manager/admin scoped CSV and styled XLSX endpoints.
- Summary rows for the complete registration roster, including absent and incomplete participants.
- Combination of owner-confirmed QR and manual evidence without exposing credentials or contact data.
- Natural ascending verified-identifier ordering with stable registration tie-break and leading-zero preservation.
- Formula-injection defense, UTF-8 CSV, text-typed XLSX identifiers, freeze pane, filter and result coloring.

Verification: `EventAttendanceReportServiceTest` passes; compilation passes.

Remaining: finalization/revision snapshots, exit/presence-cycle duration rules, restricted audit exports, report checksum/manifest and READY/FAILED outbox notifications, Mongo query/index proof, client download parity and independent HTTP/concurrency tests.
