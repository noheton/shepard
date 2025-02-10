import type {
  Collection,
  CollectionReference,
  DataObject,
  DataObjectReference,
  URIReference,
} from "@dlr-shepard/backend-client";

export type DataObjectReferenceWithPayload = DataObjectReference & {
  payload?: DataObjectReferencePayload;
};
export type DataObjectReferencePayload = DataObject & {
  collection: Collection;
};
export type Successor = DataObject & { type: "Successor" };
export type Predecessor = DataObject & { type: "Predecessor" };
export type RelatedEntity =
  | URIReference
  | DataObjectReferenceWithPayload
  | CollectionReference
  | Successor
  | Predecessor;
