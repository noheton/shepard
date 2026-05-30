/* tslint:disable */
/* eslint-disable */

import * as runtime from '../runtime';
import type { Collection } from '../models/Collection';

export interface GetCollectionByAppIdRequest {
  collectionAppId: string;
}

/**
 * Collections v2 API — GET /v2/collections/{collectionAppId}
 *
 * Manually maintained (not OpenAPI-generated). Wraps the L2d Phase A
 * endpoint that accepts a UUID v7 appId instead of the legacy numeric
 * OGM id used by the upstream CollectionApi at /shepard/api/collections.
 *
 * UX-WALK-2026-05-29-03: the frontend route loader uses this to resolve
 * an appId UUID in the URL to a full Collection (including its numeric id)
 * so that downstream v1 calls can still use the numeric id.
 */
export class CollectionV2Api extends runtime.BaseAPI {

  /**
   * GET /v2/collections/{collectionAppId}
   * Returns the Collection identified by its appId (UUID v7).
   * The response is the same CollectionIO shape as the v1 endpoint,
   * carrying both `id` (legacy long) and `appId` (canonical UUID v7).
   */
  async getCollectionByAppIdRaw(
    requestParameters: GetCollectionByAppIdRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<runtime.ApiResponse<Collection>> {
    if (requestParameters['collectionAppId'] == null) {
      throw new runtime.RequiredError(
        'collectionAppId',
        'Required parameter "collectionAppId" was null or undefined when calling getCollectionByAppId().',
      );
    }

    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request(
      {
        path: `/v2/collections/${encodeURIComponent(requestParameters['collectionAppId'])}`,
        method: 'GET',
        headers: headerParameters,
        query: {},
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse<Collection>(response);
  }

  async getCollectionByAppId(
    requestParameters: GetCollectionByAppIdRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<Collection> {
    const response = await this.getCollectionByAppIdRaw(requestParameters, initOverrides);
    return await response.value();
  }
}
