import type { StructuredDataMeta } from "./structuredDataReferenceTypes";

export type StructuredDataDataTableItem = {
  name: {
    structuredDataName: string;
    availability: StructuredDataMeta["availability"];
  };
  oid: string;
  createdAt: Date;
  actions: {
    showPayload: {
      enabled: boolean;
      payload: string;
    };
    /**
     * UI-SDREF-NO-CONTENT-001 — when the payload is available, the
     * Download icon serializes the JSON payload to a file named after
     * the structured-data entry (e.g. `tr-001-config.json`). Falls back
     * to `oid.json` when no name is set.
     */
    download: {
      enabled: boolean;
      filename: string;
      payload: string;
    };
  };
};
