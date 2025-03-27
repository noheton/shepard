import {
  instanceOfCollectionReference,
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
    id: relatedEntity.id,
    relationship: mapRelationshipType(relatedEntity),
    name: mapName(relatedEntity),
    type: mapType(relatedEntity),
    created: {
      createdAt: relatedEntity.createdAt,
      createdBy: relatedEntity.createdBy,
    },
    actions: {
      elementId: relatedEntity.id,
      annotatable: isAnnotatable(relatedEntity),
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
      path: entity.payload
        ? `/collections/${entity.payload.collectionId}/dataobjects/${entity.payload.id}`
        : undefined,
    };
  }

  if (isDeleted(entity.referencedCollectionId)) return { value: entity.name };

  return {
    value: entity.name,
    path: `/collections/${entity.referencedCollectionId}`,
  };
}

function mapType(entity: RelatedEntity): RelationshipTableElement["type"] {
  if (instanceOfURIReference(entity)) {
    return { type: `Link` };
  }
  if (instanceOfDataObject(entity)) {
    return {
      type: "Data Object",
      id: entity.id,
    };
  }
  if (instanceOfDataObjectReference(entity)) {
    if (isDeleted(entity.referencedDataObjectId))
      return {
        type: "Data Object Reference",
        availability: "deleted",
      };

    if (!entity.payload)
      return {
        type: "Data Object Reference",
        availability: "private",
        id: entity.referencedDataObjectId,
      };

    return {
      type: "Data Object Reference",
      id: entity.referencedDataObjectId,
      collectionId: entity.payload.collectionId,
      collectionName: entity.payload.collection.name,
      availability: "available",
    };
  }

  if (isDeleted(entity.referencedCollectionId))
    return { type: "Collection Reference", availability: "deleted" };
  return {
    type: "Collection Reference",
    id: entity.referencedCollectionId,
    availability: "available",
  };
}

function isAnnotatable(entity: RelatedEntity): boolean {
  return (
    instanceOfCollectionReference(entity) ||
    instanceOfDataObjectReference(entity) ||
    instanceOfURIReference(entity)
  );
}
