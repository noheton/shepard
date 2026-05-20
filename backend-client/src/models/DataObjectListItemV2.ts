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
}
