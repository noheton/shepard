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
  /**
   * Optional MDI icon name (e.g. "mdi-layers") — sets the template's
   * iconKey on create / patch. Empty string clears (UI falls back to
   * per-kind default). Design: aidocs/integrations/122.
   */
  iconKey?: string | null;
}
