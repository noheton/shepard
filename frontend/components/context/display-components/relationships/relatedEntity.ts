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

/** V2-SWEEP-004-3: v2 wire shape for kind=uri references. */
export type URIReferenceV2 = {
  id: number;
  appId: string;
  kind: "uri";
  name: string;
  createdAt: Date;
  createdBy: string;
  payload: { uri: string; relationship?: string | null };
};

export function isURIReferenceV2(entity: unknown): entity is URIReferenceV2 {
  return (
    !!entity &&
    typeof entity === "object" &&
    (entity as URIReferenceV2).kind === "uri"
  );
}

/**
 * MISSING-V2-APPID-IN-REFLISTS slice 3: v2 wire shape for kind=dataobject references.
 * The payload carries the referenced DataObject's appId, name, and its Collection's
 * appId/name so the frontend can build appId-routed links without an extra round-trip.
 */
export type DataObjectReferenceV2 = {
  id: number;
  appId: string;
  kind: "dataobject";
  name: string;
  createdAt: Date;
  createdBy: string;
  payload: {
    referencedDataObjectAppId?: string | null;
    referencedDataObjectName?: string | null;
    referencedCollectionAppId?: string | null;
    referencedCollectionName?: string | null;
    relationship?: string | null;
  };
};

export function isDataObjectReferenceV2(entity: unknown): entity is DataObjectReferenceV2 {
  return (
    !!entity &&
    typeof entity === "object" &&
    (entity as DataObjectReferenceV2).kind === "dataobject"
  );
}

/**
 * REFS-V2-PANELS-3: v2 summary shape for predecessor/successor DataObjects.
 * Mirrors the backend DataObjectSummaryIO (appId, id, name, status, createdAt, createdBy).
 * The numeric `id` is kept for the delete flow (PATCH predecessorIds) until
 * appId-keyed delete ships.
 */
export type PredecessorV2 = {
  id: number;
  appId: string;
  name: string;
  status: string;
  createdAt: Date;
  createdBy: string;
  type: "Predecessor";
};

export type SuccessorV2 = {
  id: number;
  appId: string;
  name: string;
  status: string;
  createdAt: Date;
  createdBy: string;
  type: "Successor";
};

export function isPredecessorOrSuccessorV2(entity: unknown): entity is PredecessorV2 | SuccessorV2 {
  return (
    !!entity &&
    typeof entity === "object" &&
    ((entity as PredecessorV2 | SuccessorV2).type === "Predecessor" ||
      (entity as PredecessorV2 | SuccessorV2).type === "Successor") &&
    "appId" in (entity as object) &&
    !("predecessorIds" in (entity as object))
  );
}

export type RelatedEntity =
  | URIReference
  | DataObjectReferenceWithPayload
  | DataObjectReferenceV2
  | CollectionReference
  | CollectionReferenceV2
  | URIReferenceV2
  | Successor
  | Predecessor
  | PredecessorV2
  | SuccessorV2;
