/* tslint:disable */
/* eslint-disable */
/**
 * GET /v2/users/me — enriched caller profile (CW1: watchedCollectionCount).
 * PATCH /v2/users/me — RFC 7396 JSON Merge Patch for the caller's User record.
 * Manually maintained: not generated from OpenAPI spec (v2 endpoints not yet in
 * the upstream openapi.json; will be replaced by generated code on next full
 * client-codegen run).
 */

import * as runtime from '../runtime';
import type { User } from '../models/index';
import { UserFromJSON } from '../models/index';

/**
 * CW1 — enriched profile response for GET /v2/users/me.
 * Extends the upstream User shape with v2-only additions.
 */
export interface MeIO {
  username: string;
  appId: string;
  firstName?: string | null;
  lastName?: string | null;
  email?: string | null;
  orcid?: string | null;
  displayName?: string | null;
  effectiveDisplayName: string;
  /** Number of collections the caller is currently watching (CW1). */
  watchedCollectionCount: number;
}

export interface PatchMeRequest {
  /** RFC 7396 merge-patch object. Absent fields are preserved. */
  body: {
    orcid?: string | null;
    displayName?: string | null;
  };
}

export class MeApi extends runtime.BaseAPI {
  /**
   * CW1 — Return the caller's enriched v2 profile.
   * Includes watchedCollectionCount and all upstream User fields.
   * Corresponds to GET /v2/users/me.
   */
  async getMe(): Promise<MeIO> {
    const headerParameters: runtime.HTTPHeaders = {};

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

    const response = await this.request({
      path: `/v2/users/me`,
      method: 'GET',
      headers: headerParameters,
    });

    return response.json() as Promise<MeIO>;
  }

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
        body: requestParameters.body as any,
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

  /**
   * Return the caller's preferences map.
   * Corresponds to GET /v2/users/me/preferences (U1d).
   */
  async getPreferences(): Promise<Record<string, string>> {
    const headerParameters: runtime.HTTPHeaders = {};

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

    const response = await this.request({
      path: `/v2/users/me/preferences`,
      method: 'GET',
      headers: headerParameters,
    });

    return response.json();
  }

  /**
   * Merge-patch the caller's preferences map.
   * Null values remove the key; absent keys are preserved.
   * Corresponds to PATCH /v2/users/me/preferences (U1d).
   */
  async patchPreferences(body: Record<string, string | null>): Promise<Record<string, string>> {
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

    const response = await this.request({
      path: `/v2/users/me/preferences`,
      method: 'PATCH',
      headers: headerParameters,
      body: body as any,
    });

    return response.json();
  }
}
