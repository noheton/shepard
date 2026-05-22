import type {
  EditCollectionPermissionsRequest,
  UpdateCollectionRequest,
} from "@dlr-shepard/backend-client";

export type UpdatedPermissions =
  | EditCollectionPermissionsRequest["permissions"]
  | undefined;

export type UpdatedCollection = UpdateCollectionRequest["collection"] & {
  attributes: { [key: string]: string };
  description: string;
  status?: string | null;
  heroImageUrl?: string | null;
  // LIC1 (FAIR-1): SPDX license id (free-text). Null = undeclared.
  license?: string | null;
  // LIC1 (FAIR-1): controlled accessRights enum.
  // OPEN | RESTRICTED | CLOSED | EMBARGOED, or null = undeclared.
  accessRights?: string | null;
};
