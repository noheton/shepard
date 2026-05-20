/* tslint:disable */
/* eslint-disable */

import * as runtime from '../runtime';

export interface PermissionAuditLogEntryIO {
  id: number;
  occurredAt: string;
  entityAppId: string;
  entityKind: string | null;
  actorUsername: string | null;
  action: string;
  detailJson: string | null;
}

export interface ListPermissionAuditLogRequest {
  entityAppId?: string;
  actor?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

/**
 * Admin permission audit log API — /v2/admin/permission-audit/log
 */
export class AdminPermissionAuditApi extends runtime.BaseAPI {

  /**
   * Query the permission audit log (instance-admin only)
   */
  async listPermissionAuditLogRaw(requestParameters: ListPermissionAuditLogRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<runtime.ApiResponse<PermissionAuditLogEntryIO[]>> {
    const queryParameters: any = {};

    if (requestParameters['entityAppId'] != null) {
      queryParameters['entityAppId'] = requestParameters['entityAppId'];
    }
    if (requestParameters['actor'] != null) {
      queryParameters['actor'] = requestParameters['actor'];
    }
    if (requestParameters['from'] != null) {
      queryParameters['from'] = requestParameters['from'];
    }
    if (requestParameters['to'] != null) {
      queryParameters['to'] = requestParameters['to'];
    }
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
      path: `/v2/admin/permission-audit/log`,
      method: 'GET',
      headers: headerParameters,
      query: queryParameters,
    }, initOverrides);

    return new runtime.JSONApiResponse<PermissionAuditLogEntryIO[]>(response);
  }

  /**
   * Query the permission audit log (instance-admin only)
   */
  async listPermissionAuditLog(requestParameters: ListPermissionAuditLogRequest = {}, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<PermissionAuditLogEntryIO[]> {
    const response = await this.listPermissionAuditLogRaw(requestParameters, initOverrides);
    return await response.value();
  }
}
