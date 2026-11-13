# ADR: Runtime business-action controls

Date: 2026-10-04

## Decision

Catalogued business actions use the existing versioned feature-control plane. The
first action controls are `ACTION_EVENT_CANCEL` and `ACTION_EVENT_POSTPONE`.
They are enabled by default, depend on `MODULE_EVENTS`, and are evaluated on
the server immediately before the lifecycle command lock. An administrator
cannot use a disabled parent module to re-enable a child action.

The shared universal client consumes the same manifest and hides the affected
buttons for both web and Android. The server remains authoritative: a stale
client receives a failed mutation when the action is disabled or its parent is
disabled. Action controls do not grant event-manager roles or bypass resource,
lifecycle, revision, or audit checks.

## Consequences

Existing module toggles and protected foundation safeguards remain unchanged.
New actions can be added by catalogue seed plus a guarded service command;
adding a UI key alone is not considered implementation. A future catalogue
schema may expose an explicit control type/parent relation, but this slice keeps
the existing DTO contract stable for installed clients.
