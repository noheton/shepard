export type RelationshipTableElement = {
  id: number;
  relationship: string | undefined;
  name: { value: string; path?: string };
  type:
    | LinkType
    | DataObjectType
    | DataObjectReferenceType
    | CollectionReferenceType;
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
