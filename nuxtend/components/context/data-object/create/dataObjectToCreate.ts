import type { CreateDataObjectRequest } from "@dlr-shepard/backend-client";

export type DataObjectToCreate = CreateDataObjectRequest["dataObject"] & {
  description: string;
  attributes: { [key: string]: string };
  predecessorIds: number[];
};
