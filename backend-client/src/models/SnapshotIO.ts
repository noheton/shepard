/* tslint:disable */
/* eslint-disable */

/**
 * V2b — wire shape for a Snapshot in REST responses.
 * Manually maintained; matches SnapshotIO.java exactly.
 *
 * All fields except name and description are server-managed read-only.
 */
export interface SnapshotIO {
  appId?: string | null;
  name: string;
  description?: string | null;
  /** ISO-8601 instant at which the snapshot was captured. */
  snapshotCapturedAt?: string | null;
  snapshotCreatedByUsername?: string | null;
  collectionAppId?: string | null;
  entryCount?: number;
}
