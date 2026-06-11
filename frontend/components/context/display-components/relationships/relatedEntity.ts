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

/** V2-SWEEP-004-2: v2 wire shape for kind=collection references (COLLREF-V2-APPID). */
export type CollectionReferenceV2 = {
  id: number;
  appId?: string;
  kind: "collection";
  name: string;
  createdAt: Date;
  createdBy: string;
  payload: { referencedCollectionAppId?: string | null; relationship?: string | null };
};

export function isCollectionReferenceV2(entity: unknown): entity is CollectionReferenceV2 {
  return (
    !!entity &&
    typeof entity === "object" &&
    (entity as CollectionReferenceV2).kind === "collection"
  );
}

export type RelatedEntity =
  | URIReference
  | DataObjectReferenceWithPayload
  | CollectionReference
  | CollectionReferenceV2
  | Successor
  | Predecessor;
