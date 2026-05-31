// NTF1-BACKEND-TRANSPORT-MODEL rollback — drops the appId uniqueness
// constraint on :NotificationTransport.
//
// Existing :NotificationTransport rows are preserved (DETACH DELETE is
// NOT performed) so an operator can downgrade, fix forward, then
// re-upgrade without losing transport configurations.
//
// Safe to re-run: DROP CONSTRAINT ... IF EXISTS is a no-op when the
// constraint is already absent.

DROP CONSTRAINT appId_unique_NotificationTransport IF EXISTS;
