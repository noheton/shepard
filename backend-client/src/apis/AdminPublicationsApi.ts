/* tslint:disable */
/* eslint-disable */

import * as runtime from '../runtime';

/**
 * RDM-003 — per-PID admin audit row returned by GET /v2/admin/publications.
 */
export interface AdminPublicationItemIO {
  /** UUID v7 appId of this Publication row. */
  appId: string;
  /** The minted persistent identifier. */
  pid: string;
  /** URL-segment of the entity kind (e.g. 'data-objects'). */
  entityKind: string | null;
  /** AppId of the published entity. */
  entityAppId: string | null;
  /** Wall-clock ISO-8601 at which the PID was minted. */
  mintedAt: string | null;
  /** Identifier of the minter adapter (e.g. 'local', 'epic', 'datacite'). */
  minterId: string | null;
  /** Username of the user who triggered the publish call. */
  publishedBy: string | null;
  /** 1-based version ordinal among all Publications for this entity. */
  versionNumber: number | null;
  /**
   * null on active Publications; 'retired' after DELETE /v2/{kind}/{appId}/publish.
   * KIP1f mutability marker — never deleted, always auditable.
   */
  digitalObjectMutability: string | null;
}

/**
 * RDM-003 — paginated envelope returned by GET /v2/admin/publications.
 */
export interface AdminPublicationListIO {
  /** Publications on this page, ordered mintedAt DESC. */
  items: AdminPublicationItemIO[];
  /** 0-based page index supplied by the caller. */
  page: number;
  /** Page size supplied by the caller. */
  size: number;
  /** Total count of :Publication rows across the instance. */
  totalCount: number;
}

export interface ListAdminPublicationsRequest {
  /** 0-based page index (default 0). */
  page?: number;
  /** Page size, 1–200 (default 25). */
  size?: number;
}

/**
 * RDM-003 — admin-wide PID audit list API.
 * GET /v2/admin/publications — requires instance-admin role.
 */
export class AdminPublicationsApi extends runtime.BaseAPI {

  /**
   * List all minted PIDs across the instance (instance-admin only).
   * Includes retired rows so operators can audit the full PID lifecycle.
   */
  async listAdminPublicationsRaw(
    requestParameters: ListAdminPublicationsRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction
  ): Promise<runtime.ApiResponse<AdminPublicationListIO>> {
    const queryParameters: any = {};

    if (requestParameters['page'] != null) {
      queryParameters['page'] = requestParameters['page'];
    }
    if (requestParameters['size'] != null) {
      queryParameters['size'] = requestParameters['size'];
    }

    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request({
      path: `/v2/admin/publications`,
      method: 'GET',
      headers: headerParameters,
      query: queryParameters,
    }, initOverrides);

    return new runtime.JSONApiResponse<AdminPublicationListIO>(response);
  }

  /**
   * List all minted PIDs across the instance (instance-admin only).
   * Includes retired rows so operators can audit the full PID lifecycle.
   */
  async listAdminPublications(
    requestParameters: ListAdminPublicationsRequest = {},
    initOverrides?: RequestInit | runtime.InitOverrideFunction
  ): Promise<AdminPublicationListIO> {
    const response = await this.listAdminPublicationsRaw(requestParameters, initOverrides);
    return await response.value();
  }
}
