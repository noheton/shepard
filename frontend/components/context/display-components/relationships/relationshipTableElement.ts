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
    annotatable: boolean;
  };
  created: {
    createdAt: Date;
    createdBy: string;
  };
  actions: {
    elementId: number;
    annotatable: boolean;
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
      id: number;
    }
  | {
      type: "Collection Reference";
      availability: "deleted";
    };
