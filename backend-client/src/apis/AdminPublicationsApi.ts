/* tslint:disable */
/* eslint-disable */

import * as runtime from '../runtime';

/**
 * Wire shape for a single publication row returned by
 * GET /v2/admin/publications.
 * Fields mirror PublicationIO.java exactly (RDM-003).
 */
export interface AdminPublication {
  /** Application-level identifier of the Publication row (UUID v7). */
  appId: string;
  /** The minted persistent identifier. */
  pid: string;
  /** Server-side wall-clock at mint time (ISO 8601). */
  mintedAt: string | null;
  /** Identifier of the minter that produced this row (e.g. 'local', 'epic'). */
  minterId: string;
  /** Fully-qualified resolver URL for this PID at this instance. */
  resolverUrl: string;
  /** Username of the publisher. */
  publishedBy: string | null;
  /** URL-segment of the published entity's kind (e.g. 'data-objects'). */
  entityKind: string | null;
  /** AppId of the published entity. */
  entityAppId: string | null;
  /** 1-based version number of this Publication row. */
  versionNumber: number;
  /**
   * KIP1f mutability marker.
   * null = active; 'retired' = soft-deleted after DELETE …/publish.
   */
  digitalObjectMutability: string | null;
}

/**
 * Admin publications API — GET /v2/admin/publications (instance-admin only).
 * Returns all :Publication nodes across the instance, sorted mintedAt DESC.
 * Manually maintained; not generated from OpenAPI spec (RDM-003).
 */
export class AdminPublicationsApi extends runtime.BaseAPI {

  async listPublicationsRaw(
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<runtime.ApiResponse<AdminPublication[]>> {
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
        path: `/v2/admin/publications`,
        method: 'GET',
        headers: headerParameters,
        query: {},
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse<AdminPublication[]>(response);
  }

  async listPublications(
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<AdminPublication[]> {
    const response = await this.listPublicationsRaw(initOverrides);
    return await response.value();
  }
}
