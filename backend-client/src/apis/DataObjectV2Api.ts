/* tslint:disable */
/* eslint-disable */
import * as runtime from '../runtime';
import type { DataObjectListItemV2 } from '../models/DataObjectListItemV2';

export interface ListDataObjectsV2Request {
  collectionAppId: string;
  name?: string;
  /** Filter by lifecycle status server-side (e.g. "READY", "DRAFT", "IN_REVIEW", "PUBLISHED", "ARCHIVED"). */
  status?: string;
  page?: number;
  pageSize?: number;
  /** Comma-separated enrichment flags. Pass "time-bounds" to populate timeBoundsStart/End on each item, or "full" to opt back into the pre-DB-OPT5 wire shape. */
  include?: string;
  /**
   * DB-OPT5 — flat CSV of field names to include in the response (GitHub REST convention).
   * When omitted, the default-trim shape is returned (drops description, attributes, and
   * the three deprecated `int` sibling counts). `id`, `appId`, and `name` are always
   * included as resource identity. Unknown field names produce a 400 with the offending
   * name in the response body.
   */
  fields?: string;
}

/**
 * DataObjects v2 API — GET /v2/collections/{collectionAppId}/data-objects.
 * Returns DataObjectListItemV2 rows with per-kind reference counts.
 */
export class DataObjectV2Api extends runtime.BaseAPI {

  async listDataObjectsRaw(
    requestParameters: ListDataObjectsV2Request,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<runtime.ApiResponse<DataObjectListItemV2[]>> {
    if (requestParameters['collectionAppId'] == null) {
      throw new runtime.RequiredError(
        'collectionAppId',
        'Required parameter "collectionAppId" was null or undefined when calling listDataObjects().',
      );
    }

    const queryParameters: runtime.HTTPQuery = {};
    if (requestParameters['name'] != null) queryParameters['name'] = requestParameters['name'];
    if (requestParameters['status'] != null) queryParameters['status'] = requestParameters['status'];
    if (requestParameters['page'] != null) queryParameters['page'] = requestParameters['page'];
    if (requestParameters['pageSize'] != null) queryParameters['pageSize'] = requestParameters['pageSize'];
    if (requestParameters['include'] != null) queryParameters['include'] = requestParameters['include'];
    if (requestParameters['fields'] != null) queryParameters['fields'] = requestParameters['fields'];

    const headerParameters: runtime.HTTPHeaders = {};
    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) headerParameters['Authorization'] = `Bearer ${tokenString}`;
    }

    const response = await this.request(
      {
        path: `/v2/collections/${encodeURIComponent(requestParameters['collectionAppId'])}/data-objects`,
        method: 'GET',
        headers: headerParameters,
        query: queryParameters,
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse<DataObjectListItemV2[]>(response);
  }

  async listDataObjects(
    requestParameters: ListDataObjectsV2Request,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<DataObjectListItemV2[]> {
    const response = await this.listDataObjectsRaw(requestParameters, initOverrides);
    return await response.value();
  }

  /**
   * Same as {@link listDataObjects} but also returns the total count extracted
   * from the {@code X-Total-Count} response header (or parsed from
   * {@code Content-Range} as a fallback). Returns {@code null} when neither
   * header is present (e.g. against an older backend).
   */
  async listDataObjectsWithCount(
    requestParameters: ListDataObjectsV2Request,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<{ items: DataObjectListItemV2[]; total: number | null }> {
    const raw = await this.listDataObjectsRaw(requestParameters, initOverrides);
    const items = await raw.value();

    // Prefer X-Total-Count; fall back to parsing Content-Range "unit 0-24/8514".
    let total: number | null = null;
    const xTotal = raw.raw.headers.get('X-Total-Count');
    if (xTotal != null) {
      const parsed = parseInt(xTotal, 10);
      if (!isNaN(parsed)) total = parsed;
    }
    if (total == null) {
      const contentRange = raw.raw.headers.get('Content-Range');
      if (contentRange != null) {
        const m = contentRange.match(/\/(\d+)$/);
        if (m) total = parseInt(m[1], 10);
      }
    }
    return { items, total };
  }
}
