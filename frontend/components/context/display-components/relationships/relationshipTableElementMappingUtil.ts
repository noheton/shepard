import {
  instanceOfCollectionReference,
  instanceOfDataObject,
  instanceOfDataObjectReference,
  instanceOfURIReference,
  type URIReference,
} from "@dlr-shepard/backend-client";
import type { RelatedEntity } from "./relatedEntity";
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

  return {
    id: relatedEntity.id,
    relationship: mapRelationshipType(relatedEntity),
    name: mapName(relatedEntity, parentCollectionAppId),
    information: {
      referenceId: relatedEntity.id,
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
      uriRefAppId: uriEntity?.appId,
      uriRefEditData: uriEntity
        ? {
            name: uriEntity.name,
            uri: uriEntity.uri,
            relationship: uriEntity.relationship ?? undefined,
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
  return entity.relationship ?? undefined;
}

function mapName(
  entity: RelatedEntity,
  parentCollectionAppId?: string,
): RelationshipTableElement["name"] {
  if (instanceOfURIReference(entity)) {
    return { value: entity.name, path: entity.uri };
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

  if (isDeleted(entity.referencedCollectionId)) return { value: entity.name };

  // V1-EXCEPTION (V2-LINKS): a CollectionReference's `referencedCollectionId`
  // is the numeric id of ANOTHER collection and carries no appId sibling on
  // the wire (the BasicReference payload omits it). Linking would need an
  // async numeric→appId resolve, which this synchronous mapper can't do.
  // Backlog: COLLREF-V2-APPID in aidocs/16 (add `referencedCollectionAppId`
  // to the CollectionReference v2 payload, then route on it). Until then we
  // render a non-navigable label rather than a numeric route that 404s.
  return { value: entity.name };
}

function mapType(
  entity: RelatedEntity,
): RelationshipTableElement["information"]["type"] {
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
