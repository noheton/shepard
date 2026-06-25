/* tslint:disable */
/* eslint-disable */
/**
 * SearchV2Result — response envelope from GET /v2/search.
 *
 * Hand-authored to match SearchV2ResultIO on the backend.
 * MISSING-V2-APPID-IN-SEARCH slice 2.
 */

import type { SearchV2Item } from './SearchV2Item';
import { SearchV2ItemFromJSON } from './SearchV2Item';

export interface SearchV2Result {
  items: SearchV2Item[];
  /** Total matched entities across all kinds (collections + dataobjects). */
  total: number;
  /** Zero-based page index (applies to collection results). */
  page: number;
  /** Page size used for this request. */
  pageSize: number;
  query: string;
}

export function SearchV2ResultFromJSON(json: any): SearchV2Result {
  return {
    items: (json['items'] ?? []).map(SearchV2ItemFromJSON),
    total: json['total'] ?? 0,
    page: json['page'] ?? 0,
    pageSize: json['pageSize'] ?? 50,
    query: json['query'] ?? '',
  };
}
