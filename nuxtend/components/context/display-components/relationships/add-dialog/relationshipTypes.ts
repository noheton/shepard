export enum RelationshipType {
  PREDECESSOR = "Predecessor",
  SUCCESSOR = "Successor",
  CUSTOM = "Custom",
}

export enum CustomRelationshipType {
  URI = "URI",
  COLLECTION = "Collection",
  DATA_OBJECT = "Data Object",
}

export type ReferenceData =
  | CollectionReferenceData
  | DataObjectReferenceData
  | URIReferenceData
  | PredecessorSuccessorRelationship;

export type CollectionReferenceData = {
  referencedCollectionId?: number;
  referenceName?: string;
  relationshipName?: string;
  type: CustomRelationshipType.COLLECTION;
};

export type DataObjectReferenceData = {
  referencedDataObjectId?: number;
  referenceName?: string;
  relationshipName?: string;
  type: CustomRelationshipType.DATA_OBJECT;
};

export type URIReferenceData = {
  referenceName?: string;
  referenceURI?: string;
  relationshipName?: string;
  type: CustomRelationshipType.URI;
};

export type PredecessorSuccessorRelationship = {
  relatedDataObjectId: number;
  type: RelationshipType.PREDECESSOR | RelationshipType.SUCCESSOR;
};

export function isValidPredecessorOrSuccessorReference(
  ref: ReferenceData,
): boolean {
  return (
    (ref.type === RelationshipType.PREDECESSOR ||
      ref.type === RelationshipType.SUCCESSOR) &&
    ref.relatedDataObjectId !== undefined &&
    ref.relatedDataObjectId >= 0
  );
}

export function isValidCollectionReference(ref: ReferenceData): boolean {
  return (
    ref.type === CustomRelationshipType.COLLECTION &&
    ref.referencedCollectionId !== undefined &&
    ref.referencedCollectionId >= 0 &&
    ref.referenceName !== undefined &&
    ref.referenceName.length > 0
  );
}

export function isValidDataObjectReference(ref: ReferenceData): boolean {
  return (
    ref.type === CustomRelationshipType.DATA_OBJECT &&
    ref.referencedDataObjectId !== undefined &&
    ref.referencedDataObjectId >= 0 &&
    ref.referenceName !== undefined &&
    ref.referenceName.length > 0
  );
}

export function isValidUriReference(ref: ReferenceData): boolean {
  return (
    ref.type === CustomRelationshipType.URI &&
    ref.referenceName !== undefined &&
    ref.referenceName.length > 0 &&
    ref.referenceURI !== undefined &&
    ref.referenceURI.length > 0
  );
}
