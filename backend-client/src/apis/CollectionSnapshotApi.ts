/* tslint:disable */
/* eslint-disable */

import * as runtime from '../runtime';
import type { SnapshotIO } from '../models/SnapshotIO';

/**
 * V2b — /v2/collections/{collectionAppId}/snapshots
 * Manually maintained; not generated from OpenAPI spec.
 */
export class CollectionSnapshotApi extends runtime.BaseAPI {

  /**
   * POST /v2/collections/{collectionAppId}/snapshots
   * Create a snapshot of the Collection's current state.
   * Body: { name: string, description?: string }
   * Returns 201 with the created SnapshotIO.
   */
  async createSnapshot(
    collectionAppId: string,
    body: { name: string; description?: string | null },
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<SnapshotIO> {
    const headerParameters: runtime.HTTPHeaders = {};
    headerParameters['Content-Type'] = 'application/json';

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request({
      path: `/v2/collections/${encodeURIComponent(collectionAppId)}/snapshots`,
      method: 'POST',
      headers: headerParameters,
      query: {},
      body,
    }, initOverrides);

    return response.json();
  }

  /**
   * GET /v2/collections/{collectionAppId}/snapshots
   * List all snapshots for a Collection, newest first.
   * Returns SnapshotIO[].
   */
  async listSnapshots(
    collectionAppId: string,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<SnapshotIO[]> {
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request({
      path: `/v2/collections/${encodeURIComponent(collectionAppId)}/snapshots`,
      method: 'GET',
      headers: headerParameters,
      query: {},
    }, initOverrides);

    return response.json();
  }
}
