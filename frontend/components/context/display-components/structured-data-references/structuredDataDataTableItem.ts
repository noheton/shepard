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
  };
};
