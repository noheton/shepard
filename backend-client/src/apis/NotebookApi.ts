/* tslint:disable */
/* eslint-disable */
/**
 * GET /v2/lab-journal/{dataObjectAppId}/notebooks — list .ipynb FileReferences (J1b/J1c).
 * Manually maintained — not generated from OpenAPI spec.
 */

import * as runtime from '../runtime';
import type { NotebookReferenceIO } from '../models/NotebookReferenceIO';

export class NotebookApi extends runtime.BaseAPI {

  async listNotebooks(dataObjectAppId: string): Promise<NotebookReferenceIO[]> {
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request({
      path: `/v2/lab-journal/${encodeURIComponent(dataObjectAppId)}/notebooks`,
      method: 'GET',
      headers: headerParameters,
      query: {},
    });

    return response.json();
  }
}
