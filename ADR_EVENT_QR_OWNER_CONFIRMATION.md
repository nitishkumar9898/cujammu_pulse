# ADR: Owner-confirmed event QR attendance

## Decision

Event QR values are random opaque bearer references whose SHA-256 digest is stored against a confirmed registration, event and action. A scanner never finalizes attendance from the bearer value. It creates a 90-second pending scan intent with a six-digit challenge; the authenticated registered owner must confirm that challenge. At commit, the service re-locks the credential and re-authorizes the original operator, scanner window, action, membership/grant and event lifecycle before appending immutable evidence. Registration/action uniqueness makes retries converge to one receipt.

## Safety boundary

The challenge response reduces screenshot-only proxy attendance but cannot prove physical presence or prevent collusion. The challenge is shown only in the scanner response and is stored as a digest. Credentials, intents and evidence contain no raw QR secret in DTO reports. NO_QR events cannot issue credentials. Legacy public event QR routes remain fail-closed until full owner proof, report ordering/export, notification and shared-client parity are delivered.
