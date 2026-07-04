import type {
  CollectionAccessRightsEnum,
  CollectionPromptLogModeEnum,
  EditCollectionPermissionsRequest,
  UpdateCollectionRequest,
} from "@dlr-shepard/backend-client";

export type UpdatedPermissions =
  | EditCollectionPermissionsRequest["permissions"]
  | undefined;

// V2-SWEEP-001-CLIENT-REGEN: the regenerated `Collection` model now exposes
// license / accessRights / promptLogMode as typed top-level fields. The local
// overrides keep the same optionality but adopt the client's enum types so
// assignments from a typed `Collection` type-check.
export type UpdatedCollection = UpdateCollectionRequest["collection"] & {
  attributes: { [key: string]: string };
  description: string;
  status?: string | null;
  heroImageUrl?: string | null;
  // LIC1 (FAIR-1): SPDX license id (free-text). Null = undeclared.
  license?: string | null;
  // LIC1 (FAIR-1): controlled accessRights enum.
  // OPEN | RESTRICTED | CLOSED | EMBARGOED, or null = undeclared.
  accessRights?: CollectionAccessRightsEnum | null;
  // PROMPT-h2: PromptLog storage mode.
  // HASH_ONLY | BODY_REDACTED | BODY_RAW, or null = not yet set (treated as HASH_ONLY).
  promptLogMode?: CollectionPromptLogModeEnum | null;
};
