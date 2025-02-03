import type { UpdateDataObjectRequest } from "@dlr-shepard/backend-client";

export type UpdatedDataObject = UpdateDataObjectRequest["dataObject"] & {
  description: string;
  parentId: number | null;
};
