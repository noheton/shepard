/* tslint:disable */
/* eslint-disable */

import * as runtime from '../runtime';

export interface FeatureToggleIO {
  name: string;
  enabled: boolean;
  description: string;
}

export interface PatchFeatureToggleIO {
  enabled: boolean;
}

export interface PatchFeatureRequest {
  name: string;
  patchFeatureToggleIO: PatchFeatureToggleIO;
}

/**
 * Admin feature toggles API — /v2/admin/features
 */
export class AdminFeaturesApi extends runtime.BaseAPI {

  /**
   * List all feature toggles (instance-admin only)
   */
  async listFeaturesRaw(initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<runtime.ApiResponse<FeatureToggleIO[]>> {
    const queryParameters: any = {};
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request({
      path: `/v2/admin/features`,
      method: 'GET',
      headers: headerParameters,
      query: queryParameters,
    }, initOverrides);

    return new runtime.JSONApiResponse<FeatureToggleIO[]>(response);
  }

  /**
   * List all feature toggles (instance-admin only)
   */
  async listFeatures(initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<FeatureToggleIO[]> {
    const response = await this.listFeaturesRaw(initOverrides);
    return await response.value();
  }

  /**
   * Update a feature toggle by name (instance-admin only)
   */
  async patchFeatureRaw(requestParameters: PatchFeatureRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<runtime.ApiResponse<FeatureToggleIO>> {
    if (requestParameters['name'] == null) {
      throw new runtime.RequiredError(
        'name',
        'Required parameter "name" was null or undefined when calling patchFeature().'
      );
    }

    if (requestParameters['patchFeatureToggleIO'] == null) {
      throw new runtime.RequiredError(
        'patchFeatureToggleIO',
        'Required parameter "patchFeatureToggleIO" was null or undefined when calling patchFeature().'
      );
    }

    const queryParameters: any = {};
    const headerParameters: runtime.HTTPHeaders = {};
    headerParameters['Content-Type'] = 'application/json';

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request({
      path: `/v2/admin/features/{name}`.replace(`{${'name'}}`, encodeURIComponent(String(requestParameters['name']))),
      method: 'PATCH',
      headers: headerParameters,
      query: queryParameters,
      body: requestParameters['patchFeatureToggleIO'],
    }, initOverrides);

    return new runtime.JSONApiResponse<FeatureToggleIO>(response);
  }

  /**
   * Update a feature toggle by name (instance-admin only)
   */
  async patchFeature(requestParameters: PatchFeatureRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<FeatureToggleIO> {
    const response = await this.patchFeatureRaw(requestParameters, initOverrides);
    return await response.value();
  }
}
