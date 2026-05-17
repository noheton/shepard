/* tslint:disable */
/* eslint-disable */

/**
 * POST/PATCH body for /v2/templates endpoints, per aidocs/54 §5.
 * Manually maintained; matches CreateShepardTemplateIO.java exactly.
 */
export interface CreateShepardTemplateIO {
  name: string;
  /** Template kind, e.g. DATAOBJECT_RECIPE / COLLECTION_RECIPE / EXPERIMENT_RECIPE */
  templateKind: string;
  /** Recipe body. JSON DSL per aidocs/54 §7 */
  body: string;
  description?: string | null;
  tags?: string[] | null;
}
