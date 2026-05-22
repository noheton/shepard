import type { CreateCollectionRequest } from "@dlr-shepard/backend-client";

export type CollectionToCreate = CreateCollectionRequest["collection"] & {
  description: string;
  attributes: { [key: string]: string };
  // LIC1 (FAIR-1): SPDX license id (free-text). Null = undeclared.
  license?: string | null;
  // LIC1 (FAIR-1): controlled accessRights enum value.
  accessRights?: string | null;
};
