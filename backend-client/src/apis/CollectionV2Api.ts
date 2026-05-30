/* tslint:disable */
/* eslint-disable */

import * as runtime from '../runtime';
import type { Collection } from '../models/Collection';
import { CollectionFromJSON } from '../models/Collection';

/**
 * IMPORT-NS2 — /v2/collections (POST) with optional baseline-snapshot flag.
 * Manually maintained; not generated from OpenAPI spec.
 */
export interface CreateCollectionV2Request {
  /** Collection body — same shape as the v1 create body. */
  collection: Omit<Collection, 'id' | 'createdAt' | 'createdBy' | 'updatedAt' | 'updatedBy' | 'dataObjectIds' | 'incomingIds'>;
  /**
   * IMPORT-NS2: when true, atomically creates a t=0 baseline `:Snapshot`
   * right after the Collection is persisted. The snapshot appId is returned
   * in the `X-Baseline-Snapshot-AppId` response header.
   * Omitting this parameter (or passing false) is a no-op.
   */
  createBaselineSnapshot?: boolean;
}

/** Response shape from createCollectionV2. */
export interface CreateCollectionV2Response {
  /** The newly created Collection. */
  collection: Collection;
  /**
   * IMPORT-NS2: appId of the baseline snapshot when
   * `createBaselineSnapshot=true` was passed and the snapshot was created
   * successfully. {@code null} otherwise.
   */
  baselineSnapshotAppId: string | null;
}

/**
 * Collections v2 API — POST /v2/collections.
 *
 * Exposes the {@code ?createBaselineSnapshot=true} convenience flag added by
 * IMPORT-NS2. For all other Collection operations (GET list / GET one /
 * PATCH / DELETE) continue to use the legacy {@code CollectionApi}.
 */
export class CollectionV2Api extends runtime.BaseAPI {

  /**
   * POST /v2/collections[?createBaselineSnapshot=true]
   *
   * Creates a Collection. When {@code createBaselineSnapshot=true}, also
   * mints a t=0 baseline {@code :Snapshot} atomically. The snapshot's appId
   * is surfaced via the {@code X-Baseline-Snapshot-AppId} response header
   * and returned in {@link CreateCollectionV2Response.baselineSnapshotAppId}.
   *
   * @param requestParameters - collection body + optional flag
   * @param initOverrides - optional fetch overrides
   * @returns {@link CreateCollectionV2Response}
   */
  async createCollectionV2(
    requestParameters: CreateCollectionV2Request,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<CreateCollectionV2Response> {
    const headerParameters: runtime.HTTPHeaders = {};
    headerParameters['Content-Type'] = 'application/json';

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const queryParameters: runtime.HTTPQuery = {};
    if (requestParameters.createBaselineSnapshot != null) {
      queryParameters['createBaselineSnapshot'] = requestParameters.createBaselineSnapshot;
    }

    const response = await this.request(
      {
        path: '/v2/collections',
        method: 'POST',
        headers: headerParameters,
        query: queryParameters,
        body: requestParameters.collection,
      },
      initOverrides,
    );

    const collection: Collection = CollectionFromJSON(await response.json());
    const snapshotHeader = response.headers.get('X-Baseline-Snapshot-AppId');
    return {
      collection,
      baselineSnapshotAppId: snapshotHeader ?? null,
    };
  }
}
