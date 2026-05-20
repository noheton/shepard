/* tslint:disable */
/* eslint-disable */
import * as runtime from '../runtime';
import type { DataObjectListItemV2 } from '../models/DataObjectListItemV2';

export interface ListDataObjectsV2Request {
  collectionAppId: string;
  name?: string;
  page?: number;
  size?: number;
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
    if (requestParameters['page'] != null) queryParameters['page'] = requestParameters['page'];
    if (requestParameters['size'] != null) queryParameters['size'] = requestParameters['size'];

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
}
