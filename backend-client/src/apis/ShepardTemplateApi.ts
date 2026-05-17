/* tslint:disable */
/* eslint-disable */

import * as runtime from '../runtime';
import type { ShepardTemplateIO } from '../models/ShepardTemplateIO';
import type { CreateShepardTemplateIO } from '../models/CreateShepardTemplateIO';

export interface GetTemplatesRequest {
  kind?: string;
  includeRetired?: boolean;
}

export interface GetTemplateTagsRequest {
  kind?: string;
}

export interface GetTemplateRequest {
  appId: string;
}

export interface CreateTemplateRequest {
  createShepardTemplateIO: CreateShepardTemplateIO;
}

export interface PatchTemplateRequest {
  appId: string;
  patchShepardTemplateIO: Partial<CreateShepardTemplateIO>;
}

export interface RetireTemplateRequest {
  appId: string;
}

/**
 * Shepard Template API — /v2/templates (per aidocs/54 §5).
 * Manually maintained; not generated from OpenAPI spec.
 *
 * GET list + GET by appId + GET tags are authenticated (any user).
 * POST / PATCH / DELETE require instance-admin role.
 */
export class ShepardTemplateApi extends runtime.BaseAPI {

  private async authHeaders(): Promise<runtime.HTTPHeaders> {
    const headers: runtime.HTTPHeaders = {};
    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headers['Authorization'] = `Bearer ${tokenString}`;
      }
    }
    return headers;
  }

  /**
   * List templates (latest non-retired version per name by default).
   * Admin may pass includeRetired=true to see retired rows.
   */
  async getTemplatesRaw(requestParameters: GetTemplatesRequest = {}, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<runtime.ApiResponse<ShepardTemplateIO[]>> {
    const queryParameters: any = {};
    if (requestParameters['kind'] != null) queryParameters['kind'] = requestParameters['kind'];
    if (requestParameters['includeRetired'] != null) queryParameters['includeRetired'] = requestParameters['includeRetired'];

    const headerParameters = await this.authHeaders();

    const response = await this.request({
      path: `/v2/templates`,
      method: 'GET',
      headers: headerParameters,
      query: queryParameters,
    }, initOverrides);

    return new runtime.JSONApiResponse<ShepardTemplateIO[]>(response);
  }

  async getTemplates(requestParameters: GetTemplatesRequest = {}, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<ShepardTemplateIO[]> {
    const response = await this.getTemplatesRaw(requestParameters, initOverrides);
    return await response.value();
  }

  /**
   * Read one template by appId.
   */
  async getTemplateRaw(requestParameters: GetTemplateRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<runtime.ApiResponse<ShepardTemplateIO>> {
    if (requestParameters['appId'] == null) {
      throw new runtime.RequiredError('appId', 'Required parameter "appId" was null or undefined when calling getTemplate().');
    }

    const headerParameters = await this.authHeaders();

    const response = await this.request({
      path: `/v2/templates/{appId}`.replace(`{${'appId'}}`, encodeURIComponent(String(requestParameters['appId']))),
      method: 'GET',
      headers: headerParameters,
      query: {},
    }, initOverrides);

    return new runtime.JSONApiResponse<ShepardTemplateIO>(response);
  }

  async getTemplate(requestParameters: GetTemplateRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<ShepardTemplateIO> {
    const response = await this.getTemplateRaw(requestParameters, initOverrides);
    return await response.value();
  }

  /**
   * Distinct list of tags across all non-retired templates (authenticated users).
   */
  async getTemplateTagsRaw(requestParameters: GetTemplateTagsRequest = {}, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<runtime.ApiResponse<string[]>> {
    const queryParameters: any = {};
    if (requestParameters['kind'] != null) queryParameters['kind'] = requestParameters['kind'];

    const headerParameters = await this.authHeaders();

    const response = await this.request({
      path: `/v2/templates/tags`,
      method: 'GET',
      headers: headerParameters,
      query: queryParameters,
    }, initOverrides);

    return new runtime.JSONApiResponse<string[]>(response);
  }

  async getTemplateTags(requestParameters: GetTemplateTagsRequest = {}, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<string[]> {
    const response = await this.getTemplateTagsRaw(requestParameters, initOverrides);
    return await response.value();
  }

  /**
   * Mint a new template (version=1). Admin-only.
   */
  async createTemplateRaw(requestParameters: CreateTemplateRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<runtime.ApiResponse<ShepardTemplateIO>> {
    if (requestParameters['createShepardTemplateIO'] == null) {
      throw new runtime.RequiredError('createShepardTemplateIO', 'Required parameter "createShepardTemplateIO" was null or undefined when calling createTemplate().');
    }

    const headerParameters = await this.authHeaders();
    headerParameters['Content-Type'] = 'application/json';

    const response = await this.request({
      path: `/v2/templates`,
      method: 'POST',
      headers: headerParameters,
      query: {},
      body: requestParameters['createShepardTemplateIO'],
    }, initOverrides);

    return new runtime.JSONApiResponse<ShepardTemplateIO>(response);
  }

  async createTemplate(requestParameters: CreateTemplateRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<ShepardTemplateIO> {
    const response = await this.createTemplateRaw(requestParameters, initOverrides);
    return await response.value();
  }

  /**
   * Edit a template (triggers copy-on-write versioning). Admin-only.
   * Prior row is retired; new row is minted with version+1.
   */
  async patchTemplateRaw(requestParameters: PatchTemplateRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<runtime.ApiResponse<ShepardTemplateIO>> {
    if (requestParameters['appId'] == null) {
      throw new runtime.RequiredError('appId', 'Required parameter "appId" was null or undefined when calling patchTemplate().');
    }
    if (requestParameters['patchShepardTemplateIO'] == null) {
      throw new runtime.RequiredError('patchShepardTemplateIO', 'Required parameter "patchShepardTemplateIO" was null or undefined when calling patchTemplate().');
    }

    const headerParameters = await this.authHeaders();
    headerParameters['Content-Type'] = 'application/json';

    const response = await this.request({
      path: `/v2/templates/{appId}`.replace(`{${'appId'}}`, encodeURIComponent(String(requestParameters['appId']))),
      method: 'PATCH',
      headers: headerParameters,
      query: {},
      body: requestParameters['patchShepardTemplateIO'],
    }, initOverrides);

    return new runtime.JSONApiResponse<ShepardTemplateIO>(response);
  }

  async patchTemplate(requestParameters: PatchTemplateRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<ShepardTemplateIO> {
    const response = await this.patchTemplateRaw(requestParameters, initOverrides);
    return await response.value();
  }

  /**
   * Retire a template (soft-delete). Admin-only.
   * Sets retired=true; row stays on disk so existing citations remain valid.
   * Returns 204 No Content.
   */
  async retireTemplateRaw(requestParameters: RetireTemplateRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<runtime.ApiResponse<void>> {
    if (requestParameters['appId'] == null) {
      throw new runtime.RequiredError('appId', 'Required parameter "appId" was null or undefined when calling retireTemplate().');
    }

    const headerParameters = await this.authHeaders();

    const response = await this.request({
      path: `/v2/templates/{appId}`.replace(`{${'appId'}}`, encodeURIComponent(String(requestParameters['appId']))),
      method: 'DELETE',
      headers: headerParameters,
      query: {},
    }, initOverrides);

    return new runtime.VoidApiResponse(response);
  }

  async retireTemplate(requestParameters: RetireTemplateRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<void> {
    await this.retireTemplateRaw(requestParameters, initOverrides);
  }
}
