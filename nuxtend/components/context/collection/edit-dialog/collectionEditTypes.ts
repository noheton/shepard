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
};
