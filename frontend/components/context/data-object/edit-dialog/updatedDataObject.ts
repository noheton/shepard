import type {
  DataObjectAccessRightsEnum,
  UpdateDataObjectRequest,
} from "@dlr-shepard/backend-client";

// V2-SWEEP-001-CLIENT-REGEN: the regenerated `DataObject` model now exposes
// license / accessRights as typed top-level fields. The local override adopts
// the client's enum type so assignments from a typed `DataObject` type-check.
export type UpdatedDataObject = UpdateDataObjectRequest["dataObject"] & {
  description: string;
  parentId: number | null;
  attributes: { [key: string]: string };
  predecessorIds: number[];
  status?: string | null;
  // LIC1 (FAIR-1): SPDX license id (free-text). Null = undeclared.
  license?: string | null;
  // LIC1 (FAIR-1): controlled accessRights enum value.
  accessRights?: DataObjectAccessRightsEnum | null;
};
