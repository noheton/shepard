/* tslint:disable */
/* eslint-disable */

import * as runtime from '../runtime';

/**
 * W3C SPARQL Results JSON — binding value shape.
 */
export interface SparqlResultValue {
  type: 'uri' | 'literal' | 'bnode';
  value: string;
  /** XML Schema datatype URI (for typed literals) */
  datatype?: string;
  /** Language tag (for plain literals with language) */
  'xml:lang'?: string;
}

/**
 * One row in a SPARQL SELECT result set.
 * Keys are the variable names from the SELECT clause.
 */
export type SparqlBinding = Record<string, SparqlResultValue | undefined>;

/**
 * W3C SPARQL Results JSON format (application/sparql-results+json).
 * https://www.w3.org/TR/sparql11-results-json/
 */
export interface SparqlResultsJson {
  head: { vars: string[] };
  results: { bindings: SparqlBinding[] };
}

export interface SparqlQueryRequest {
  /** appId of the SemanticRepository to query against. */
  repoAppId: string;
  /** SPARQL SELECT or ASK query string. */
  query: string;
}

/**
 * SPARQL proxy — N1f
 * POST /v2/semantic/{repoAppId}/sparql (application/x-www-form-urlencoded)
 */
export class SemanticSparqlApi extends runtime.BaseAPI {

  /**
   * Execute a read-only SPARQL SELECT or ASK query against a SemanticRepository.
   * Uses the POST form variant (SPARQL 1.1 Protocol §2.1.3) to avoid URL length
   * limits for large queries.
   *
   * Only SELECT and ASK are permitted; mutation forms are rejected 400 by
   * SparqlQueryValidator on the server side.
   */
  async sparqlQueryRaw(
    requestParameters: SparqlQueryRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<runtime.ApiResponse<SparqlResultsJson>> {
    if (requestParameters.repoAppId == null) {
      throw new runtime.RequiredError(
        'repoAppId',
        'Required parameter "repoAppId" was null or undefined when calling sparqlQuery().',
      );
    }
    if (requestParameters.query == null) {
      throw new runtime.RequiredError(
        'query',
        'Required parameter "query" was null or undefined when calling sparqlQuery().',
      );
    }

    const headerParameters: runtime.HTTPHeaders = {
      'Content-Type': 'application/x-www-form-urlencoded',
      'Accept': 'application/sparql-results+json',
    };

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const formBody = 'query=' + encodeURIComponent(requestParameters.query);

    const response = await this.request({
      path: `/v2/semantic/{repoAppId}/sparql`.replace(
        '{repoAppId}',
        encodeURIComponent(String(requestParameters.repoAppId)),
      ),
      method: 'POST',
      headers: headerParameters,
      body: formBody,
    }, initOverrides);

    return new runtime.JSONApiResponse<SparqlResultsJson>(response);
  }

  /**
   * Execute a read-only SPARQL SELECT or ASK query against a SemanticRepository.
   */
  async sparqlQuery(
    requestParameters: SparqlQueryRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<SparqlResultsJson> {
    const response = await this.sparqlQueryRaw(requestParameters, initOverrides);
    return await response.value();
  }
}
