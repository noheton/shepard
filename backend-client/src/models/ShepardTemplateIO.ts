/* tslint:disable */
/* eslint-disable */

/**
 * Wire shape for GET /v2/templates/... responses, per aidocs/54 §5.
 * Manually maintained; matches ShepardTemplateIO.java exactly.
 */
export interface ShepardTemplateIO {
  appId: string;
  name: string;
  /** Template kind, e.g. DATAOBJECT_RECIPE / COLLECTION_RECIPE / EXPERIMENT_RECIPE */
  templateKind: string;
  version: number;
  /** Recipe body. JSON DSL per aidocs/54 §7 */
  body: string;
  description?: string | null;
  tags?: string[] | null;
  createdBy?: string | null;
  /** Millis since epoch when the row was created */
  createdAt?: number | null;
  /** Millis since epoch when the row was last touched */
  updatedAt?: number | null;
  retired: boolean;
}
