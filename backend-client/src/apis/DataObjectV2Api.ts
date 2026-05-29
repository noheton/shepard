/* tslint:disable */
/* eslint-disable */
import * as runtime from '../runtime';
import type { DataObjectListItemV2 } from '../models/DataObjectListItemV2';

export interface ListDataObjectsV2Request {
  collectionAppId: string;
  name?: string;
  status?: string;
  page?: number;
  size?: number;
  include?: string;
  fields?: string;
}

export interface DataObjectListWithCountV2 {
  items: DataObjectListItemV2[];
  total: number;
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
    if (requestParameters['size'] != null) queryParameters['size'] = requestParameters['size'];
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

  async listDataObjectsWithCountRaw(
    requestParameters: ListDataObjectsV2Request,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<runtime.ApiResponse<DataObjectListWithCountV2>> {
    if (requestParameters['collectionAppId'] == null) {
      throw new runtime.RequiredError(
        'collectionAppId',
        'Required parameter "collectionAppId" was null or undefined when calling listDataObjectsWithCount().',
      );
    }

    const queryParameters: runtime.HTTPQuery = {};
    if (requestParameters['name'] != null) queryParameters['name'] = requestParameters['name'];
    if (requestParameters['status'] != null) queryParameters['status'] = requestParameters['status'];
    if (requestParameters['page'] != null) queryParameters['page'] = requestParameters['page'];
    if (requestParameters['size'] != null) queryParameters['size'] = requestParameters['size'];
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
        path: `/v2/collections/${encodeURIComponent(requestParameters['collectionAppId'])}/data-objects/count`,
        method: 'GET',
        headers: headerParameters,
        query: queryParameters,
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse<DataObjectListWithCountV2>(response);
  }

  async listDataObjectsWithCount(
    requestParameters: ListDataObjectsV2Request,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<DataObjectListWithCountV2> {
    const response = await this.listDataObjectsWithCountRaw(requestParameters, initOverrides);
    return await response.value();
  }
}
