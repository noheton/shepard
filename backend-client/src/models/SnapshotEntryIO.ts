/* tslint:disable */
/* eslint-disable */

/**
 * V2b — wire shape for a single SnapshotEntry in the snapshot manifest.
 * Manually maintained; matches SnapshotEntryIO.java exactly.
 *
 * The manifest endpoint (GET /v2/snapshots/{appId}/manifest) returns
 * a flat array of these records — one per entity captured in the snapshot.
 */
export interface SnapshotEntryIO {
  entityAppId: string;
  revision: number;
}
