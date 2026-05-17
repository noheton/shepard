/* tslint:disable */
/* eslint-disable */

/**
 * V2e — wire shape for the response of the snapshot diff endpoint
 * (GET /v2/snapshots/{aAppId}/diff/{bAppId}).
 * Manually maintained; matches SnapshotDiffIO.java + nested DiffEntry exactly.
 */
export interface SnapshotDiffEntryIO {
  entityAppId: string;
  revisionA: number;
  revisionB: number;
}

export interface SnapshotDiffIO {
  snapshotAAppId: string;
  snapshotBAppId: string;
  /** Epoch milliseconds at which snapshot A was captured. */
  snapshotACapturedAtMs: number;
  /** Epoch milliseconds at which snapshot B was captured. */
  snapshotBCapturedAtMs: number;
  /** entityAppIds present in B but not in A. Sorted ascending. */
  added: string[];
  /** entityAppIds present in A but not in B. Sorted ascending. */
  removed: string[];
  /** Entities present in both snapshots with different revision values. */
  changed: SnapshotDiffEntryIO[];
  /** Count of entities present in both snapshots with identical revisions. */
  unchangedCount: number;
}
