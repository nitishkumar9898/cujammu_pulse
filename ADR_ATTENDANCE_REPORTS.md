# ADR: Scoped deterministic attendance reports

## Decision

Event managers and administrators receive two server-generated projections: a CSV summary and a styled XLSX summary. Both include every registration, including absent/incomplete/cancelled rows, and combine accepted owner-confirmed QR and reviewed manual ENTRY evidence. Rows sort globally by the verified identifier using a natural comparator (`CS-2 < CS-10`) with stable registration tie-break; leading zeros remain strings. XLSX cells are explicitly typed as text and status colors are presentation only. CSV text is UTF-8 with BOM and formula-injection defense.

## Safety boundary

The report contains no raw QR payload, credential digest, private contact fields, challenge, or unrestricted audit attempts. Event ownership and the runtime events flag are checked server-side. A checkpoint is reported as point evidence and never upgraded to duration/full-attendance without the separate entry/exit policy and reviewed finalization workflow.
