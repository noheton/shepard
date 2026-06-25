/* tslint:disable */
/* eslint-disable */
/**
 * SearchV2Api — hand-authored client for GET /v2/search.
 *
 * The generated SearchApi targets POST /shepard/api/search (v1).
 * This class targets the appId-keyed v2 surface introduced by
 * MISSING-V2-APPID-IN-SEARCH.
 *
 * Usage: useV2ShepardApi(SearchV2Api).value.globalSearch({ q: 'foo' })
 */

import * as runtime from '../runtime';
import type { SearchV2Result } from '../models/SearchV2Result';
import { SearchV2ResultFromJSON } from '../models/SearchV2Result';

export interface GlobalSearchRequest {
  q: string;
  page?: number;
  pageSize?: number;
}

export class SearchV2Api extends runtime.BaseAPI {
  /**
   * Full-text search returning appId-keyed results.
   * Maps to: GET /v2/search?q=...&page=...&pageSize=...
   */
  async globalSearchRaw(
    requestParameters: GlobalSearchRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<runtime.ApiResponse<SearchV2Result>> {
    const queryParameters: any = {};
    queryParameters['q'] = requestParameters.q;
    if (requestParameters.page != null) {
      queryParameters['page'] = requestParameters.page;
    }
    if (requestParameters.pageSize != null) {
      queryParameters['pageSize'] = requestParameters.pageSize;
    }

    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.apiKey) {
      headerParameters['X-API-KEY'] = await this.configuration.apiKey('X-API-KEY');
    }

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request(
      {
        path: `/v2/search`,
        method: 'GET',
        headers: headerParameters,
        query: queryParameters,
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse(response, jsonValue => SearchV2ResultFromJSON(jsonValue));
  }

  async globalSearch(
    requestParameters: GlobalSearchRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<SearchV2Result> {
    const response = await this.globalSearchRaw(requestParameters, initOverrides);
    return await response.value();
  }
}
