// NTF1-BACKEND-TRANSPORT-MODEL — :NotificationTransport scaffold.
//
// Introduces the runtime-mutable transport-config entity that backs
// `GET/POST/PATCH/DELETE /v2/admin/notifications/transports`. Unlike the
// :JupyterConfig / :UnhideConfig singleton nodes, this entity is
// list-shaped: an instance can have N transports (one SMTP relay, two
// Matrix homeservers, …) each addressed by its appId.
//
// This migration is purely additive — no existing data is mutated. The
// uniqueness constraint on appId:
//   1. Guarantees the singleton invariant the DAO assumes for
//      findByAppId() / deleteByAppId().
//   2. Creates an implicit Neo4j index so per-appId lookups are O(log n).
//
// Idempotent: CREATE CONSTRAINT ... IF NOT EXISTS is a no-op when the
// constraint already exists. Safe to re-run.
//
// Rollback: V96_R__Ntf1_NotificationTransport_scaffold.cypher
//
// Operator runbook: docs/admin/runbooks/notification-transports.md (TBD —
// the runbook is queued as NTF1-RUNBOOK in aidocs/16; until it lands,
// see aidocs/integrations/40-notification-system.md for the operator
// view of the field set + write-only secrets contract.)
//
// Aborts startup on error per MigrationsRunner's fail-fast posture.

CREATE CONSTRAINT appId_unique_NotificationTransport IF NOT EXISTS
  FOR (n:NotificationTransport)
  REQUIRE n.appId IS UNIQUE;
