import {
  instanceOfCollectionReference,
  instanceOfDataObject,
  instanceOfDataObjectReference,
  instanceOfURIReference,
  type URIReference,
} from "@dlr-shepard/backend-client";
import { isCollectionReferenceV2, isDataObjectReferenceV2, isURIReferenceV2, type RelatedEntity } from "./relatedEntity";
import type { RelationshipTableElement } from "./relationshipTableElement";
import { readCollectionAppId, readDataObjectAppId } from "~/utils/appId";

export function mapRelatedEntityToRelationshipTableElement(
  relatedEntity: RelatedEntity,
  // V2-LINKS: the appId of the collection this relationships table is rendered
  // for (= the current route param, already a UUID). Predecessor / Successor /
  // sibling DataObjects all live in this same collection, so their route uses
  // this appId for the collection segment + their own appId for the DO segment.
  // Never the numeric collectionId — the v2 detail route 404s on it.
  parentCollectionAppId?: string,
): RelationshipTableElement {
  const isUri = instanceOfURIReference(relatedEntity);
  // REF-EDIT-6: carry appId + edit-seed for URI references.
  // The generated URIReference type lacks appId, so we access it via
  // a type assertion — the backend serialises it via BasicEntityIO.
  const uriEntity = isUri
    ? (relatedEntity as URIReference & { appId?: string })
    : undefined;
  // V2-SWEEP-004-3: v2 URI reference wire shape (kind=uri, appId always present).
  const isUriV2 = isURIReferenceV2(relatedEntity);

  return {
    id: relatedEntity.id,
    relationship: mapRelationshipType(relatedEntity),
    name: mapName(relatedEntity, parentCollectionAppId),
    information: {
      referenceId: relatedEntity.id,
      referenceAppId: readReferenceAppId(relatedEntity),
      referenceKind: mapReferenceKind(relatedEntity),
      type: mapType(relatedEntity),
      annotatable: isAnnotatable(relatedEntity),
    },
    created: {
      createdAt: relatedEntity.createdAt,
      createdBy: relatedEntity.createdBy,
    },
    actions: {
      elementId: relatedEntity.id,
      annotatable: isAnnotatable(relatedEntity),
      referenceAppId: readReferenceAppId(relatedEntity),
      referenceKind: mapReferenceKind(relatedEntity),
      uriRefAppId: uriEntity?.appId ?? (isUriV2 ? relatedEntity.appId : undefined),
      uriRefEditData: uriEntity
        ? {
            name: uriEntity.name,
            uri: uriEntity.uri,
            relationship: uriEntity.relationship ?? undefined,
          }
        : isUriV2
          ? {
              name: relatedEntity.name,
              uri: relatedEntity.payload.uri,
              relationship: relatedEntity.payload.relationship ?? undefined,
            }
          : undefined,
    },
  };
}

function mapRelationshipType(
  entity: RelatedEntity,
): RelationshipTableElement["relationship"] {
  if (instanceOfDataObject(entity)) {
    return entity.type;
  }
  if (isCollectionReferenceV2(entity) || isURIReferenceV2(entity) || isDataObjectReferenceV2(entity)) {
    return (entity.payload.relationship ?? undefined) as string | undefined;
  }
  return entity.relationship ?? undefined;
}

function mapName(
  entity: RelatedEntity,
  parentCollectionAppId?: string,
): RelationshipTableElement["name"] {
  if (instanceOfURIReference(entity)) {
    return { value: entity.name, path: entity.uri };
  }
  if (isURIReferenceV2(entity)) {
    return { value: entity.name, path: entity.payload.uri };
  }
  if (instanceOfDataObject(entity)) {
    // Predecessor / Successor / sibling DataObjects share the parent's
    // collection. V2-LINKS: route on the parent collection appId + the
    // entity's own appId; never numeric ids (the v2 route 404s on them).
    const doAppId = readDataObjectAppId(entity);
    if (!parentCollectionAppId || !doAppId) return { value: entity.name };
    return {
      value: entity.name,
      path: `/collections/${parentCollectionAppId}/dataobjects/${doAppId}`,
    };
  }
  if (instanceOfDataObjectReference(entity)) {
    // The payload carries the FULL referenced collection (with appId) +
    // the referenced DataObject (with appId) — both available on the wire.
    const colAppId = readCollectionAppId(entity.payload?.collection);
    const doAppId = readDataObjectAppId(entity.payload);
    return {
      value: entity.name,
      path:
        entity.payload && colAppId && doAppId
          ? `/collections/${colAppId}/dataobjects/${doAppId}`
          : undefined,
    };
  }

  // MISSING-V2-APPID-IN-REFLISTS slice 3: v2 DataObject reference — payload carries
  // referencedDataObjectAppId/Name and referencedCollectionAppId/Name directly.
  if (isDataObjectReferenceV2(entity)) {
    const refDoAppId = entity.payload.referencedDataObjectAppId;
    const refColAppId = entity.payload.referencedCollectionAppId;
    const displayName = entity.payload.referencedDataObjectName ?? entity.name;
    return {
      value: displayName,
      path:
        refColAppId && refDoAppId
          ? `/collections/${refColAppId}/dataobjects/${refDoAppId}`
          : undefined,
    };
  }

  // V2-SWEEP-004-2: v2 collection reference — payload carries referencedCollectionAppId.
  if (isCollectionReferenceV2(entity)) {
    const refColAppId = entity.payload.referencedCollectionAppId;
    return {
      value: entity.name,
      path: refColAppId ? `/collections/${refColAppId}` : undefined,
    };
  }

  if (isDeleted(entity.referencedCollectionId)) return { value: entity.name };

  // V1-EXCEPTION (V2-LINKS): v1 CollectionReference carries only the numeric
  // referencedCollectionId — no appId on the wire. Renders as a non-navigable
  // label. Cleared once the v2 endpoint (COLLREF-V2-APPID backend, V2-SWEEP-004-1)
  // is used, which happens when dataObjectAppId is passed to useRelatedEntities.
  return { value: entity.name };
}

function mapType(
  entity: RelatedEntity,
): RelationshipTableElement["information"]["type"] {
  if (instanceOfURIReference(entity)) {
    return { type: `Link` };
  }
  if (isURIReferenceV2(entity)) {
    return { type: "Link" };
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

  if (isDataObjectReferenceV2(entity)) {
    const refDoAppId = entity.payload.referencedDataObjectAppId;
    const refColName = entity.payload.referencedCollectionName ?? undefined;
    if (!refDoAppId)
      return { type: "Data Object Reference", availability: "deleted" };
    return {
      type: "Data Object Reference",
      collectionName: refColName,
      availability: "available",
    };
  }

  if (isCollectionReferenceV2(entity)) {
    const refColAppId = entity.payload.referencedCollectionAppId;
    if (!refColAppId) return { type: "Collection Reference", availability: "deleted" };
    return { type: "Collection Reference", collectionAppId: refColAppId, availability: "available" };
  }

  if (isDeleted(entity.referencedCollectionId))
    return { type: "Collection Reference", availability: "deleted" };
  return {
    type: "Collection Reference",
    id: entity.referencedCollectionId,
    availability: "available",
  };
}

/**
 * V2-only annotation path: the reference node's own appId. Annotatable
 * relationship rows are CollectionReference / DataObjectReference / URIReference
 * nodes, all of which carry their appId on the wire (the generated types don't
 * always expose it, so read defensively).
 */
function readReferenceAppId(entity: RelatedEntity): string | undefined {
  return (entity as unknown as { appId?: string }).appId ?? undefined;
}

/**
 * Concrete reference kind for the v2 polymorphic annotation subject. Mirrors
 * the backend `subjectKind` labels (CollectionReference / DataObjectReference /
 * URIReference). DataObjects are not annotated via this table.
 */
function mapReferenceKind(entity: RelatedEntity): string | undefined {
  if (instanceOfURIReference(entity) || isURIReferenceV2(entity))
    return "URIReference";
  if (instanceOfDataObjectReference(entity) || isDataObjectReferenceV2(entity))
    return "DataObjectReference";
  if (instanceOfCollectionReference(entity) || isCollectionReferenceV2(entity))
    return "CollectionReference";
  return undefined;
}

function isAnnotatable(entity: RelatedEntity): boolean {
  return (
    instanceOfCollectionReference(entity) ||
    isCollectionReferenceV2(entity) ||
    instanceOfDataObjectReference(entity) ||
    isDataObjectReferenceV2(entity) ||
    instanceOfURIReference(entity) ||
    isURIReferenceV2(entity)
  );
}
