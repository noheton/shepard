// VID1c rollback — drops the :VideoConfig.appId uniqueness constraint.
//
// Run this before downgrading to a version prior to VID1c.
// If any :VideoConfig nodes exist, they are preserved — only the
// uniqueness constraint is removed.
//
// Safe to re-run: DROP CONSTRAINT ... IF EXISTS is a no-op when
// the constraint is already absent.

DROP CONSTRAINT appId_unique_VideoConfig IF EXISTS;
