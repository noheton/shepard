/* tslint:disable */
/* eslint-disable */
/**
 * shepard backend — v2 provenance surface.
 *
 * Hand-authored client for `GET /v2/provenance/...`, mirroring the
 * style of the other generated `/v2/` API classes (e.g. `GitReferenceApi`).
 * Added for PROV1d (frontend dashboard); will be replaced by a generated
 * client once the v2 OpenAPI bundle picks up these endpoints.
 *
 * Backend reference: `de.dlr.shepard.v2.provenance.resources.ProvenanceRest`.
 */

import * as runtime from '../runtime';

/** Wire shape of one row returned by `GET /v2/provenance/activities`. */
export interface ActivityIO {
  appId: string;
  /** CREATE | READ | UPDATE | DELETE | EXECUTE */
  actionKind: string;
  targetKind?: string | null;
  targetAppId?: string | null;
  agentUsername: string;
  summary: string;
  startedAtMillis: number;
  endedAtMillis: number | null;
  method?: string | null;
  path?: string | null;
  status?: number | null;
  originInstance?: string | null;
}

/** Wire shape of `GET /v2/provenance/stats` payload. */
export interface ProvenanceStatsIO {
  scope: string;
  id: string | null;
  sinceMillis: number;
  untilMillis: number;
  bucketMillis: number;
  totalCount: number;
  distinctAgents: number;
  /** { CREATE: N, READ: M, UPDATE: O, DELETE: P, EXECUTE: Q } */
  totalsByActionKind: Record<string, number>;
  /** Each entry is `[bucketStartMillis, count]`. Empty buckets NOT filled. */
  buckets: number[][];
  /** Each entry is `[bucketStartMillis, runningTotal]`. Same alignment as `buckets`. */
  cumulative: number[][];
  /** At-query-time entity counts for the scope; null when scope=user. */
  contentCensus?: Record<string, number> | null;
  /** At-query-time byte sums for the scope; null when scope=user. */
  byteTotals?: Record<string, number> | null;
}

export interface ProvenanceStatsParams {
  /** "instance" | "collection" | "user". */
  scope: string;
  /** appId for scope=collection; username for scope=user. */
  id?: string;
  /** Inclusive lower bound on startedAt (millis since epoch). */
  since?: number;
  /** Inclusive upper bound on startedAt (millis since epoch). */
  until?: number;
}

export interface ListActivitiesParams {
  agent?: string;
  targetKind?: string;
  targetAppId?: string;
  since?: number;
  until?: number;
  limit?: number;
}

export class ProvenanceApi extends runtime.BaseAPI {

  async getStats(params: ProvenanceStatsParams): Promise<ProvenanceStatsIO> {
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const query: Record<string, string | number> = { scope: params.scope };
    if (params.id !== undefined && params.id !== null) query['id'] = params.id;
    if (params.since !== undefined && params.since !== null) query['since'] = params.since;
    if (params.until !== undefined && params.until !== null) query['until'] = params.until;

    const response = await this.request({
      path: `/v2/provenance/stats`,
      method: 'GET',
      headers: headerParameters,
      query,
    });

    return response.json();
  }

  async listActivities(params: ListActivitiesParams = {}): Promise<ActivityIO[]> {
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const query: Record<string, string | number> = {};
    if (params.agent !== undefined) query['agent'] = params.agent;
    if (params.targetKind !== undefined) query['targetKind'] = params.targetKind;
    if (params.targetAppId !== undefined) query['targetAppId'] = params.targetAppId;
    if (params.since !== undefined) query['since'] = params.since;
    if (params.until !== undefined) query['until'] = params.until;
    if (params.limit !== undefined) query['limit'] = params.limit;

    const response = await this.request({
      path: `/v2/provenance/activities`,
      method: 'GET',
      headers: headerParameters,
      query,
    });

    return response.json();
  }
}
