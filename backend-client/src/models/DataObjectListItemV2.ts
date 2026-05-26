/* tslint:disable */
/* eslint-disable */
import type { DataObject } from './DataObject';

/**
 * DataObject list item enriched with per-kind reference counts (v2).
 * Returned by GET /v2/collections/{collectionAppId}/data-objects.
 */
export interface DataObjectListItemV2 extends DataObject {
  timeseriesCount: number;
  fileCount: number;
  structuredDataCount: number;
  /** Earliest data-point timestamp in ns since epoch. Null when no TS data or `?include=time-bounds` not requested. */
  timeBoundsStart?: number | null;
  /** Latest data-point timestamp in ns since epoch. Null when no TS data or `?include=time-bounds` not requested. */
  timeBoundsEnd?: number | null;
}
