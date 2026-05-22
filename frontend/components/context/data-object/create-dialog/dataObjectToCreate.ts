import type { CreateDataObjectRequest } from "@dlr-shepard/backend-client";

export type DataObjectToCreate = CreateDataObjectRequest["dataObject"] & {
  description: string;
  attributes: { [key: string]: string };
  predecessorIds: number[];
  // LIC1 (FAIR-1): SPDX license id (free-text). Null = undeclared.
  license?: string | null;
  // LIC1 (FAIR-1): controlled accessRights enum value.
  accessRights?: string | null;
};
