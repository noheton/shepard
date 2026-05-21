/* tslint:disable */
/* eslint-disable */
/**
 * CW1 — Collection watches (user subscribes to a Collection) REST client.
 *
 * Endpoints:
 *   GET    /v2/collections/{collectionAppId}/watches        — list all watchers
 *   GET    /v2/collections/{collectionAppId}/watches/me    — check if caller is watching
 *   POST   /v2/collections/{collectionAppId}/watches        — start watching (idempotent)
 *   DELETE /v2/collections/{collectionAppId}/watches/me    — stop watching (idempotent)
 *
 * Manually maintained: not generated from OpenAPI spec (v2 endpoints not in
 * the upstream openapi.json; will be replaced by generated code on next full
 * client-codegen run).
 */

import * as runtime from '../runtime';

export interface CollectionWatcherIO {
  watcherAppId: string;
  username: string;
  collectionAppId: string;
  since: number;
}

export interface CollectionWatchesListRequest {
  collectionAppId: string;
}

export interface CollectionWatchMeRequest {
  collectionAppId: string;
}

export interface CollectionWatchRequest {
  collectionAppId: string;
}

export interface CollectionUnwatchRequest {
  collectionAppId: string;
}

function buildHeaders(configuration: runtime.Configuration | undefined): runtime.HTTPHeaders {
  const h: runtime.HTTPHeaders = {
    Accept: 'application/json',
  };
  if (configuration?.apiKey) {
    // apiKey may be a string or async fn — the runtime BaseAPI resolves it; mirror that here.
    // Since this client is written manually without the BaseAPI call pattern for apiKey,
    // we handle it synchronously for the string case.
    const apiKey = configuration.apiKey;
    if (typeof apiKey === 'string') {
      h['X-API-KEY'] = apiKey;
    }
  }
  return h;
}

export class CollectionWatchesApi extends runtime.BaseAPI {

  /** List all watchers for a Collection. Requires Read on the Collection. */
  async listWatchers(
    requestParameters: CollectionWatchesListRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<CollectionWatcherIO[]> {
    const headerParameters: runtime.HTTPHeaders = { Accept: 'application/json' };

    if (this.configuration?.apiKey) {
      headerParameters['X-API-KEY'] = await this.configuration.apiKey('X-API-KEY');
    }
    if (this.configuration?.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) headerParameters['Authorization'] = `Bearer ${tokenString}`;
    }

    const response = await this.request(
      {
        path: `/v2/collections/${encodeURIComponent(requestParameters.collectionAppId)}/watches`,
        method: 'GET',
        headers: headerParameters,
      },
      initOverrides,
    );

    return response.json();
  }

  /**
   * Check whether the caller is watching a Collection.
   * Returns the watcher record when watching, throws with 404 when not.
   */
  async getMyWatch(
    requestParameters: CollectionWatchMeRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<CollectionWatcherIO> {
    const headerParameters: runtime.HTTPHeaders = { Accept: 'application/json' };

    if (this.configuration?.apiKey) {
      headerParameters['X-API-KEY'] = await this.configuration.apiKey('X-API-KEY');
    }
    if (this.configuration?.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) headerParameters['Authorization'] = `Bearer ${tokenString}`;
    }

    const response = await this.request(
      {
        path: `/v2/collections/${encodeURIComponent(requestParameters.collectionAppId)}/watches/me`,
        method: 'GET',
        headers: headerParameters,
      },
      initOverrides,
    );

    return response.json();
  }

  /**
   * Start watching a Collection (idempotent).
   * Returns the watch record (existing or newly created).
   */
  async watch(
    requestParameters: CollectionWatchRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<CollectionWatcherIO> {
    const headerParameters: runtime.HTTPHeaders = { Accept: 'application/json' };

    if (this.configuration?.apiKey) {
      headerParameters['X-API-KEY'] = await this.configuration.apiKey('X-API-KEY');
    }
    if (this.configuration?.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) headerParameters['Authorization'] = `Bearer ${tokenString}`;
    }

    const response = await this.request(
      {
        path: `/v2/collections/${encodeURIComponent(requestParameters.collectionAppId)}/watches`,
        method: 'POST',
        headers: headerParameters,
      },
      initOverrides,
    );

    return response.json();
  }

  /**
   * Stop watching a Collection (idempotent).
   * Returns 204 whether or not the caller was watching.
   */
  async unwatch(
    requestParameters: CollectionUnwatchRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<void> {
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration?.apiKey) {
      headerParameters['X-API-KEY'] = await this.configuration.apiKey('X-API-KEY');
    }
    if (this.configuration?.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) headerParameters['Authorization'] = `Bearer ${tokenString}`;
    }

    await this.request(
      {
        path: `/v2/collections/${encodeURIComponent(requestParameters.collectionAppId)}/watches/me`,
        method: 'DELETE',
        headers: headerParameters,
      },
      initOverrides,
    );
  }
}
