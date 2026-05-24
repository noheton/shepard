import type { UpdateDataObjectRequest } from "@dlr-shepard/backend-client";

export type UpdatedDataObject = UpdateDataObjectRequest["dataObject"] & {
  description: string;
  parentId: number | null;
  attributes: { [key: string]: string };
  predecessorIds: number[];
  status?: string | null;
  // LIC1 (FAIR-1): SPDX license id (free-text). Null = undeclared.
  license?: string | null;
  // LIC1 (FAIR-1): controlled accessRights enum value.
  accessRights?: string | null;
};
