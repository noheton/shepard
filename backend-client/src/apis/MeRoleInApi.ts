/**
 * GET /v2/me/role-in/{collectionAppId} — caller's role in a Collection.
 *
 * Returns booleans for {read, write, manage, isInstanceAdmin} so the
 * role-in-context chip (U1c2) can render in a single fetch.
 *
 * Manually maintained: not generated from OpenAPI spec (v2 endpoints
 * not yet in the upstream openapi.json; will be replaced by generated
 * code on next full client-codegen run).
 */

import * as runtime from '../runtime';
import type { MeRoleInIO } from '../models/MeRoleInIO';

export class MeRoleInApi extends runtime.BaseAPI {
  /**
   * Fetch the caller's role in the given Collection.
   *
   * 200 — at least one role (or instance-admin).
   * 401 — unauthenticated.
   * 403 — Collection exists, caller has no roles and is not an admin.
   * 404 — no Collection with the supplied appId.
   */
  async getRoleIn(collectionAppId: string): Promise<MeRoleInIO> {
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
      path: `/v2/me/role-in/${encodeURIComponent(collectionAppId)}`,
      method: 'GET',
      headers: headerParameters,
    });

    return response.json();
  }
}
