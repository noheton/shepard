/* tslint:disable */
/* eslint-disable */
/**
 * PATCH /v2/users/me — RFC 7396 JSON Merge Patch for the caller's User record.
 * Manually maintained: not generated from OpenAPI spec (v2 endpoints not yet in
 * the upstream openapi.json; will be replaced by generated code on next full
 * client-codegen run).
 */

import * as runtime from '../runtime';
import type { User } from '../models/index';
import { UserFromJSON } from '../models/index';

export interface PatchMeRequest {
  /** RFC 7396 merge-patch object. Absent fields are preserved. */
  body: {
    orcid?: string | null;
    displayName?: string | null;
  };
}

export class MeApi extends runtime.BaseAPI {
  /**
   * Partial-update the caller's User record.
   * v1 (U1a/U1b) patches `orcid` and `displayName`.
   * Empty-string clears the field; null clears; absent preserves.
   */
  async patchMeRaw(
    requestParameters: PatchMeRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<runtime.ApiResponse<User>> {
    const headerParameters: runtime.HTTPHeaders = {
      'Content-Type': 'application/merge-patch+json',
    };

    if (this.configuration?.apiKey) {
      headerParameters['X-API-KEY'] = await this.configuration.apiKey('X-API-KEY');
    }

    if (this.configuration?.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request(
      {
        path: `/v2/users/me`,
        method: 'PATCH',
        headers: headerParameters,
        body: JSON.stringify(requestParameters.body),
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse(response, jsonValue => UserFromJSON(jsonValue));
  }

  async patchMe(
    requestParameters: PatchMeRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<User> {
    const response = await this.patchMeRaw(requestParameters, initOverrides);
    return await response.value();
  }
}
