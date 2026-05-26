/* tslint:disable */
/* eslint-disable */
/**
 * TM1a — PATCH /v2/timeseries-references/{appId}
 *
 * Merge-patch for the three time-reference fields on a TimeseriesReference:
 *   - timeReference  (WALL_CLOCK | EXPERIMENT_RELATIVE)
 *   - wallClockOffset  (UTC nanoseconds of experiment t=0)
 *   - wallClockOffsetSource  (provenance tag)
 *
 * Only fields present in the request body are updated; absent fields are
 * left unchanged on the server. Returns the full updated TimeseriesReference.
 *
 * 400 when timeReference is EXPERIMENT_RELATIVE and wallClockOffset is
 * absent from both the patch and the stored entity.
 *
 * Fork addition — not in upstream 5.2.0.
 */

import * as runtime from '../runtime';
import type { TimeseriesReference } from '../models/TimeseriesReference';
import { TimeseriesReferenceFromJSON } from '../models/TimeseriesReference';

export interface PatchTimeReferenceRequest {
  /** appId (UUID v7) of the TimeseriesReference to patch. */
  appId: string;
  /** Merge-patch body; only fields present here are updated. */
  body: TimeReferenceV2Patch;
}

/**
 * Patchable TM1 fields — all optional (RFC 7396 merge-patch).
 * To clear a nullable field, set it to null.
 */
export interface TimeReferenceV2Patch {
  /** WALL_CLOCK | EXPERIMENT_RELATIVE */
  timeReference?: string | null;
  /** UTC nanoseconds of DAQ t=0. Required when timeReference = EXPERIMENT_RELATIVE. */
  wallClockOffset?: number | null;
  /** Free-text provenance tag (e.g. "GPS sync", "NTP_marker", "manual"). */
  wallClockOffsetSource?: string | null;
}

/**
 * TM1a v2 API — PATCH /v2/timeseries-references/{appId}.
 */
export class TimeseriesReferenceV2Api extends runtime.BaseAPI {

  async patchTimeReferenceRaw(
    request: PatchTimeReferenceRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<runtime.ApiResponse<TimeseriesReference>> {
    if (request.appId == null) {
      throw new runtime.RequiredError(
        'appId',
        'Required parameter "appId" was null or undefined when calling patchTimeReference().',
      );
    }

    const headerParameters: runtime.HTTPHeaders = {
      'Content-Type': 'application/merge-patch+json',
    };

    if (this.configuration?.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) headerParameters['Authorization'] = `Bearer ${tokenString}`;
    }

    const response = await this.request(
      {
        path: `/v2/timeseries-references/${encodeURIComponent(request.appId)}`,
        method: 'PATCH',
        headers: headerParameters,
        body: JSON.stringify(request.body),
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse<TimeseriesReference>(response, json =>
      TimeseriesReferenceFromJSON(json),
    );
  }

  async patchTimeReference(
    request: PatchTimeReferenceRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<TimeseriesReference> {
    const response = await this.patchTimeReferenceRaw(request, initOverrides);
    return await response.value();
  }
}
