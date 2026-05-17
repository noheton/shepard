/* tslint:disable */
/* eslint-disable */

import * as runtime from '../runtime';
import type { ShepardTemplateIO } from '../models/ShepardTemplateIO';
import type { DataObject } from '../models/DataObject';

export interface ListAllowedTemplatesRequest {
  collectionAppId: string;
}

export interface InstantiateDataObjectRequest {
  collectionAppId: string;
  templateAppId: string;
  name?: string;
}

export interface RecordTemplateUsageRequest {
  collectionAppId: string;
  templateAppId: string;
}

/**
 * Collection-scoped template endpoints — /v2/collections/{appId}/templates/...
 *
 * T1c  POST /v2/collections/{appId}/templates/from/{templateAppId}    → records :USES_TEMPLATE edge
 * T1d  GET  /v2/collections/{appId}/templates/allowed                 → allow-listed templates
 * T1e  POST /v2/collections/{collectionAppId}/data-objects/from-template/{templateAppId} → DataObject
 */
export class CollectionTemplateApi extends runtime.BaseAPI {

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

  async listAllowedTemplatesRaw(
    requestParameters: ListAllowedTemplatesRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<runtime.ApiResponse<ShepardTemplateIO[]>> {
    if (requestParameters['collectionAppId'] == null) {
      throw new runtime.RequiredError('collectionAppId', 'Required parameter "collectionAppId" was null or undefined.');
    }

    const headerParameters = await this.authHeaders();

    const response = await this.request({
      path: `/v2/collections/{appId}/templates/allowed`.replace(
        `{${'appId'}}`,
        encodeURIComponent(String(requestParameters['collectionAppId'])),
      ),
      method: 'GET',
      headers: headerParameters,
      query: {},
    }, initOverrides);

    return new runtime.JSONApiResponse<ShepardTemplateIO[]>(response);
  }

  async listAllowedTemplates(
    requestParameters: ListAllowedTemplatesRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<ShepardTemplateIO[]> {
    const response = await this.listAllowedTemplatesRaw(requestParameters, initOverrides);
    return await response.value();
  }

  async instantiateDataObjectRaw(
    requestParameters: InstantiateDataObjectRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<runtime.ApiResponse<DataObject>> {
    if (requestParameters['collectionAppId'] == null) {
      throw new runtime.RequiredError('collectionAppId', 'Required parameter "collectionAppId" was null or undefined.');
    }
    if (requestParameters['templateAppId'] == null) {
      throw new runtime.RequiredError('templateAppId', 'Required parameter "templateAppId" was null or undefined.');
    }

    const headerParameters = await this.authHeaders();
    headerParameters['Content-Type'] = 'application/json';

    const body: Record<string, unknown> = {};
    if (requestParameters['name'] != null) body['name'] = requestParameters['name'];

    const response = await this.request({
      path: `/v2/collections/{collectionAppId}/data-objects/from-template/{templateAppId}`
        .replace(`{${'collectionAppId'}}`, encodeURIComponent(String(requestParameters['collectionAppId'])))
        .replace(`{${'templateAppId'}}`, encodeURIComponent(String(requestParameters['templateAppId']))),
      method: 'POST',
      headers: headerParameters,
      query: {},
      body,
    }, initOverrides);

    return new runtime.JSONApiResponse<DataObject>(response);
  }

  async instantiateDataObject(
    requestParameters: InstantiateDataObjectRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<DataObject> {
    const response = await this.instantiateDataObjectRaw(requestParameters, initOverrides);
    return await response.value();
  }

  async recordTemplateUsageRaw(
    requestParameters: RecordTemplateUsageRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<runtime.ApiResponse<void>> {
    if (requestParameters['collectionAppId'] == null) {
      throw new runtime.RequiredError('collectionAppId', 'Required parameter "collectionAppId" was null or undefined.');
    }
    if (requestParameters['templateAppId'] == null) {
      throw new runtime.RequiredError('templateAppId', 'Required parameter "templateAppId" was null or undefined.');
    }

    const headerParameters = await this.authHeaders();

    const response = await this.request({
      path: `/v2/collections/{appId}/templates/from/{templateAppId}`
        .replace(`{${'appId'}}`, encodeURIComponent(String(requestParameters['collectionAppId'])))
        .replace(`{${'templateAppId'}}`, encodeURIComponent(String(requestParameters['templateAppId']))),
      method: 'POST',
      headers: headerParameters,
      query: {},
    }, initOverrides);

    return new runtime.VoidApiResponse(response);
  }

  async recordTemplateUsage(
    requestParameters: RecordTemplateUsageRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<void> {
    await this.recordTemplateUsageRaw(requestParameters, initOverrides);
  }
}
