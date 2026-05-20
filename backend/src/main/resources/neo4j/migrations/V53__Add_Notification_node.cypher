// NTF1a — in-app notification system.
// Adds unique constraint on Notification.appId and an index on targetUsername
// to keep the per-user list query cheap.
// Safe to re-run: IF NOT EXISTS makes both statements idempotent.

CREATE CONSTRAINT Notification_appId_unique IF NOT EXISTS
FOR (n:Notification) REQUIRE n.appId IS UNIQUE;

CREATE INDEX Notification_targetUsername IF NOT EXISTS
FOR (n:Notification) ON (n.targetUsername);
