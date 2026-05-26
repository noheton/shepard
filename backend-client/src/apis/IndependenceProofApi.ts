/* tslint:disable */
/* eslint-disable */
import * as runtime from '../runtime';

/**
 * TPL11 — IndependenceProofApi
 * POST /v2/quality/independence-proof
 */

export interface IndependenceProofRequest {
  /** AppIds of DataObjects in set A (e.g. training set). */
  setA: string[];
  /** AppIds of DataObjects in set B (e.g. test set). */
  setB: string[];
}

export interface SharedAncestor {
  /** The appId of the shared ancestor DataObject. */
  ancestorAppId: string;
  /** AppIds from setA that have this ancestor within 10 hops. */
  reachableFromA: string[];
  /** AppIds from setB that have this ancestor within 10 hops. */
  reachableFromB: string[];
}

export interface SharedAnnotation {
  /** The annotation key (attribute name). */
  key: string;
  /** The shared annotation value. */
  value: string;
  /** AppIds from setA that carry this annotation. */
  fromA: string[];
  /** AppIds from setB that carry this annotation. */
  fromB: string[];
}

export interface IndependenceProofResult {
  /** True when no shared ancestors and no shared annotation key-value pairs were found. */
  independent: boolean;
  /**
   * Shared provenance ancestor entries (empty when independent).
   * Each entry is a DataObject that is an ancestor (within 10 hops) of at least
   * one member of setA and at least one member of setB.
   */
  sharedAncestors: SharedAncestor[];
  /**
   * Annotation key-value pairs appearing on at least one DataObject in each set.
   * Empty when independent.
   */
  sharedAnnotations: SharedAnnotation[];
  /** ISO-8601 timestamp when the check was performed. */
  checkedAt: string;
}

/**
 * TPL11 — Independence proof endpoint.
 * Checks whether two DataObject sets are mutually independent with respect
 * to provenance ancestry (within 10 hops) and shared annotation key-value pairs.
 */
export class IndependenceProofApi extends runtime.BaseAPI {

  /**
   * POST /v2/quality/independence-proof
   *
   * Runs the independence check and returns the result.
   *
   * @param body - The two DataObject appId sets to check.
   * @returns IndependenceProofResult with `independent`, `sharedAncestors`, `sharedAnnotations`, and `checkedAt`.
   */
  async checkIndependenceRaw(
    body: IndependenceProofRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<runtime.ApiResponse<IndependenceProofResult>> {
    const headerParameters: runtime.HTTPHeaders = {};
    headerParameters['Content-Type'] = 'application/json';

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) headerParameters['Authorization'] = `Bearer ${tokenString}`;
    }

    const response = await this.request(
      {
        path: '/v2/quality/independence-proof',
        method: 'POST',
        headers: headerParameters,
        body: JSON.stringify(body),
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse<IndependenceProofResult>(response);
  }

  async checkIndependence(
    body: IndependenceProofRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<IndependenceProofResult> {
    const response = await this.checkIndependenceRaw(body, initOverrides);
    return await response.value();
  }
}
