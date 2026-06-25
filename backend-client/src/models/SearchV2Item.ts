/* tslint:disable */
/* eslint-disable */
/**
 * SearchV2Item — a single result from GET /v2/search.
 *
 * Hand-authored to match SearchV2ItemIO on the backend.
 * MISSING-V2-APPID-IN-SEARCH slice 2.
 */

export interface SearchV2Item {
  /** UUID v7 application-level identifier. */
  appId: string;
  name: string;
  /** Discriminator: "collection" or "dataobject". */
  kind: 'collection' | 'dataobject';
  /**
   * AppId of the owning Collection — set for kind=dataobject, null/undefined
   * for kind=collection. Enables the frontend to construct the navigation route
   * without a secondary collection-lookup call.
   */
  parentCollectionAppId?: string | null;
}

export function SearchV2ItemFromJSON(json: any): SearchV2Item {
  return {
    appId: json['appId'],
    name: json['name'],
    kind: json['kind'],
    parentCollectionAppId: json['parentCollectionAppId'] ?? null,
  };
}
