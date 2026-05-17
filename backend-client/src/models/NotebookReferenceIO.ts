/* tslint:disable */
/* eslint-disable */
/**
 * Wire shape returned by GET /v2/lab-journal/{dataObjectAppId}/notebooks (J1b).
 * Manually maintained — not generated from OpenAPI spec.
 */

export interface NotebookReferenceIO {
  appId: string;
  fileName: string;
  fileSize?: number | null;
  mimeType?: string | null;
  createdAt?: Date | null;
  createdBy?: string | null;
  referenceKind: 'SINGLETON' | 'BUNDLE_FILE';
}
