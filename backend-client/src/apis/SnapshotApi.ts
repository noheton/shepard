/* tslint:disable */
/* eslint-disable */

import * as runtime from '../runtime';
import type { SnapshotIO } from '../models/SnapshotIO';
import type { SnapshotEntryIO } from '../models/SnapshotEntryIO';
import type { SnapshotDiffIO } from '../models/SnapshotDiffIO';

/**
 * V2b/V2e — /v2/snapshots/{snapshotAppId}
 * Manually maintained; not generated from OpenAPI spec.
 */
export class SnapshotApi extends runtime.BaseAPI {

  /**
   * GET /v2/snapshots/{snapshotAppId}
   * Read snapshot metadata.
   * Returns SnapshotIO.
   */
  async getSnapshot(
    snapshotAppId: string,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<SnapshotIO> {
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request({
      path: `/v2/snapshots/${encodeURIComponent(snapshotAppId)}`,
      method: 'GET',
      headers: headerParameters,
      query: {},
    }, initOverrides);

    return response.json();
  }

  /**
   * GET /v2/snapshots/{snapshotAppId}/manifest
   * Read the full snapshot manifest (all entity appId + revision pairs).
   * Returns SnapshotEntryIO[].
   */
  async getSnapshotManifest(
    snapshotAppId: string,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<SnapshotEntryIO[]> {
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request({
      path: `/v2/snapshots/${encodeURIComponent(snapshotAppId)}/manifest`,
      method: 'GET',
      headers: headerParameters,
      query: {},
    }, initOverrides);

    return response.json();
  }

  /**
   * DELETE /v2/snapshots/{snapshotAppId}
   * Soft-delete a snapshot. Returns 204 No Content.
   */
  async deleteSnapshot(
    snapshotAppId: string,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<void> {
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    await this.request({
      path: `/v2/snapshots/${encodeURIComponent(snapshotAppId)}`,
      method: 'DELETE',
      headers: headerParameters,
      query: {},
    }, initOverrides);
  }

  /**
   * GET /v2/snapshots/{aAppId}/diff/{bAppId}
   * Diff two snapshots (A = base, B = head).
   * Returns SnapshotDiffIO.
   */
  async diffSnapshots(
    aAppId: string,
    bAppId: string,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<SnapshotDiffIO> {
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request({
      path: `/v2/snapshots/${encodeURIComponent(aAppId)}/diff/${encodeURIComponent(bAppId)}`,
      method: 'GET',
      headers: headerParameters,
      query: {},
    }, initOverrides);

    return response.json();
  }
}
