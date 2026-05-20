/* tslint:disable */
/* eslint-disable */

import * as runtime from '../runtime';
import type { ContainerSummary } from '../models/ContainerSummary';

export interface ListReferencedContainersRequest {
  collectionAppId: string;
}

/**
 * Collection referenced-containers API — CC2
 * GET /v2/collections/{collectionAppId}/referenced-containers
 */
export class CollectionContainersApi extends runtime.BaseAPI {

  async listReferencedContainersRaw(
    requestParameters: ListReferencedContainersRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<runtime.ApiResponse<ContainerSummary[]>> {
    if (requestParameters['collectionAppId'] == null) {
      throw new runtime.RequiredError(
        'collectionAppId',
        'Required parameter "collectionAppId" was null or undefined when calling listReferencedContainers().',
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
        path: `/v2/collections/${encodeURIComponent(requestParameters['collectionAppId'])}/referenced-containers`,
        method: 'GET',
        headers: headerParameters,
        query: {},
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse<ContainerSummary[]>(response);
  }

  async listReferencedContainers(
    requestParameters: ListReferencedContainersRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<ContainerSummary[]> {
    const response = await this.listReferencedContainersRaw(requestParameters, initOverrides);
    return await response.value();
  }
}
