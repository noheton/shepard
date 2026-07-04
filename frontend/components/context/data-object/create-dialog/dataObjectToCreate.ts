import type { CreateDataObjectV2Request } from "@dlr-shepard/backend-client";

// Writable fields accepted by POST /v2/collections/{appId}/data-objects.
export type DataObjectToCreate = CreateDataObjectV2Request["createDataObjectV2"] & {
  // Make these required at the type level; the v2 body accepts them as optional.
  description: string;
  attributes: { [key: string]: string };
  predecessorIds: number[];
};
