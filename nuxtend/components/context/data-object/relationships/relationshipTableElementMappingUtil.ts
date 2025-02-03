import {
  instanceOfDataObject,
  instanceOfDataObjectReference,
  instanceOfURIReference,
} from "@dlr-shepard/backend-client";
import type { RelatedEntity } from "./relatedEntity";
import type { RelationshipTableElement } from "./relationshipTableElement";

export function mapRelatedEntityToRelationshipTableElement(
  relatedEntity: RelatedEntity,
): RelationshipTableElement {
  return {
    relationship: mapRelationshipType(relatedEntity),
    name: mapName(relatedEntity),
    type: mapType(relatedEntity),
    created: {
      createdAt: relatedEntity.createdAt,
      createdBy: relatedEntity.createdBy,
    },
  };
}

function mapRelationshipType(
  entity: RelatedEntity,
): RelationshipTableElement["relationship"] {
  if (instanceOfURIReference(entity)) {
    return undefined;
  }
  if (instanceOfDataObject(entity)) {
    return entity.type;
  }
  return entity.relationship ?? undefined;
}

function mapName(entity: RelatedEntity): RelationshipTableElement["name"] {
  if (instanceOfURIReference(entity)) {
    return { value: entity.name, path: entity.uri };
  }
  if (instanceOfDataObject(entity)) {
    return {
      value: entity.name,
      path: `/collections/${entity.collectionId}/dataobjects/${entity.id}`,
    };
  }
  if (instanceOfDataObjectReference(entity)) {
    return {
      value: entity.name,
      path: `/collections/${entity.payload.collectionId}/dataobjects/${entity.payload.id}`,
    };
  }

  return {
    value: entity.name,
    path: `/collections/${entity.referencedCollectionId}`,
  };
}

function mapType(entity: RelatedEntity): RelationshipTableElement["type"] {
  if (instanceOfURIReference(entity)) {
    return { value: `Link` };
  }
  if (instanceOfDataObject(entity)) {
    return {
      value: `Data Object (ID ${entity.id})`,
    };
  }
  if (instanceOfDataObjectReference(entity)) {
    return {
      value: `Data Object (ID ${entity.referencedDataObjectId})`,
      collection: `In Collection ${entity.payload.collection.name} (ID: ${entity.payload.collectionId})`,
    };
  }
  return {
    value: `Collection (ID ${entity.referencedCollectionId})`,
  };
}
