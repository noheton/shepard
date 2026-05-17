/* tslint:disable */
/* eslint-disable */

import * as runtime from '../runtime';

/**
 * Wire shape for GET /v2/admin/metrics-summary.
 * Fields match AdminMetricsSummaryIO.java exactly.
 */
export interface AdminMetricsSummary {
  jvmHeapUsedBytes: number;
  jvmHeapMaxBytes: number;
  uptimeMillis: number;
  httpRequestsTotal: number;
  httpMeanRequestMillis: number | null;
  permissionsCacheHits: number;
  permissionsCacheMisses: number;
  permissionsCacheHitRatio: number | null;
}

/**
 * Admin metrics API — GET /v2/admin/metrics-summary (instance-admin only).
 * Manually maintained; not generated from OpenAPI spec.
 */
export class AdminMetricsApi extends runtime.BaseAPI {

  async getMetricsSummaryRaw(initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<runtime.ApiResponse<AdminMetricsSummary>> {
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request({
      path: `/v2/admin/metrics-summary`,
      method: 'GET',
      headers: headerParameters,
      query: {},
    }, initOverrides);

    return new runtime.JSONApiResponse<AdminMetricsSummary>(response);
  }

  async getMetricsSummary(initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<AdminMetricsSummary> {
    const response = await this.getMetricsSummaryRaw(initOverrides);
    return await response.value();
  }
}
