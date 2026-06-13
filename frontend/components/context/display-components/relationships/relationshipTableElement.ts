export type RelationshipTableElement = {
  id: number;
  relationship: string | undefined;
  /**
   * PROV1k — optional typed predecessor relationship type for Predecessor entries.
   * One of: "prov:wasInformedBy", "prov:wasRevisionOf", "fair2r:repairs".
   * Absent (undefined) for non-predecessor relationship types.
   */
  predecessorRelationshipType?: string;
  name: { value: string; path?: string };
  information: {
    type:
      | LinkType
      | DataObjectType
      | DataObjectReferenceType
      | CollectionReferenceType;
    referenceId: number;
    /** V2-only annotation path: the reference node's appId (drives /v2/annotations). */
    referenceAppId?: string;
    /** Concrete reference kind for the v2 polymorphic annotation subject. */
    referenceKind?: string;
    annotatable: boolean;
  };
  created: {
    createdAt: Date;
    createdBy: string;
  };
  actions: {
    elementId: number;
    annotatable: boolean;
    /** V2-only annotation path: the reference node's appId (drives /v2/annotations). */
    referenceAppId?: string;
    /** Concrete reference kind for the v2 polymorphic annotation subject. */
    referenceKind?: string;
    /** REF-EDIT-6: UUID v7 appId, present only for URIReference rows. */
    uriRefAppId?: string;
    /** REF-EDIT-6: Current name / uri / relationship for pre-filling the edit dialog. */
    uriRefEditData?: {
      name: string;
      uri: string;
      relationship?: string;
    };
  };
};

type LinkType = { type: "Link" };
type DataObjectType = { type: "Data Object"; id: number };
export type DataObjectReferenceType =
  | {
      type: "Data Object Reference";
      availability: "available";
      id: number;
      collectionName: string;
      collectionId: number;
    }
  | {
      type: "Data Object Reference";
      availability: "private";
      id: number;
    }
  | {
      type: "Data Object Reference";
      availability: "deleted";
    };
export type CollectionReferenceType =
  | {
      type: "Collection Reference";
      availability: "available";
      /** v1 path: numeric Neo4j id of the referenced collection. */
      id?: number;
      /** v2 path (V2-SWEEP-004-2): UUID v7 appId of the referenced collection. */
      collectionAppId?: string;
    }
  | {
      type: "Collection Reference";
      availability: "deleted";
    };
