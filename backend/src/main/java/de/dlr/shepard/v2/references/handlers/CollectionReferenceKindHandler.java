package de.dlr.shepard.v2.references.handlers;

import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.dataobject.daos.CollectionReferenceDAO;
import de.dlr.shepard.context.references.dataobject.entities.CollectionReference;
import de.dlr.shepard.context.references.dataobject.io.CollectionReferenceIO;
import de.dlr.shepard.context.references.dataobject.services.CollectionReferenceService;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import de.dlr.shepard.v2.references.spi.ReferenceKindHandler;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * V2-SWEEP-004-1 — in-tree {@link ReferenceKindHandler} for {@code kind=collection}.
 * Delegates wholly to the existing {@link CollectionReferenceService}; no logic is
 * duplicated. Payload key set: {@code {referencedCollectionAppId, relationship}}.
 *
 * <p>Pre-feature rows whose {@code referencedCollection} carries no {@code appId}
 * (i.e. the node was created before HasAppId was applied to Collection) will resolve
 * {@code referencedCollectionAppId} as {@code null} in the IO — consistent with the
 * "return null, never backfill" contract for pre-feature rows.
 */
@RequestScoped
public class CollectionReferenceKindHandler implements ReferenceKindHandler {

  @Inject
  CollectionReferenceService collectionReferenceService;

  @Inject
  CollectionReferenceDAO collectionReferenceDAO;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  CollectionDAO collectionDAO;

  @Override
  public String kind() {
    return "collection";
  }

  @Override
  public boolean owns(BasicReference reference) {
    return reference instanceof CollectionReference;
  }

  @Override
  public BasicReference findByAppId(String appId) {
    return collectionReferenceService.findByAppId(appId);
  }

  @Override
  public ReferenceV2IO toIO(BasicReference reference) {
    CollectionReference ref = (CollectionReference) reference;
    ReferenceV2IO io = new ReferenceV2IO(ref, kind());
    Collection referencedCollection = ref.getReferencedCollection();
    io.put(
      "referencedCollectionAppId",
      referencedCollection != null ? referencedCollection.getAppId() : null
    );
    io.put("relationship", ref.getRelationship());
    return io;
  }

  @Override
  public ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body) {
    if (body == null) throw new BadRequestException("create body is required for kind=collection");
    DataObject parent = resolveParent(dataObjectAppId);

    Object rawAppId = body.get("referencedCollectionAppId");
    if (rawAppId == null) {
      throw new BadRequestException("referencedCollectionAppId is required for kind=collection");
    }
    Collection referenced = collectionDAO.findByAppId(rawAppId.toString());
    if (referenced == null) {
      throw new NotFoundException("No Collection with appId " + rawAppId);
    }

    CollectionReferenceIO ioIn = new CollectionReferenceIO();
    ioIn.setName(asString(body.get("name")));
    ioIn.setReferencedCollectionId(referenced.getShepardId());
    ioIn.setRelationship(asString(body.get("relationship")));

    CollectionReference created = collectionReferenceService.createReference(
      parent.getCollection().getShepardId(),
      parent.getShepardId(),
      ioIn
    );
    return toIO(created);
  }

  @Override
  public ReferenceV2IO patch(String appId, Map<String, Object> patch) {
    CollectionReference updated = collectionReferenceService.patchReferenceByAppId(appId, patch);
    return toIO(updated);
  }

  @Override
  public void delete(String appId) {
    CollectionReference ref = collectionReferenceService.findByAppId(appId);
    if (ref == null) throw new NotFoundException("No CollectionReference with appId " + appId);
    DataObject parent = ref.getDataObject();
    if (parent == null || parent.getCollection() == null) {
      throw new NotFoundException("CollectionReference " + appId + " has no resolvable parent DataObject");
    }
    collectionReferenceService.deleteReference(
      parent.getCollection().getShepardId(),
      parent.getShepardId(),
      ref.getShepardId()
    );
  }

  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind) {
    // APISIMP-URI-COLL-DOREF-NONPAGED-APPID: use the appId-keyed DAO path directly rather
    // than resolving the DataObject numeric Neo4j id via resolveParent() + getAllReferencesByDataObjectId().
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    List<CollectionReference> refs = collectionReferenceDAO.findByDataObjectAppId(dataObjectAppId);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (CollectionReference ref : refs) {
      if (ref != null && !ref.isDeleted()) out.add(toIO(ref));
    }
    return out;
  }

  @Override
  public int countByDataObject(String dataObjectAppId, String subKind) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    return collectionReferenceDAO.countByDataObjectAppId(dataObjectAppId);
  }

  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind, int skip, int limit) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    List<CollectionReference> refs = collectionReferenceDAO.findByDataObjectAppId(dataObjectAppId, skip, limit);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (CollectionReference ref : refs) {
      if (ref != null && !ref.isDeleted()) out.add(toIO(ref));
    }
    return out;
  }

  private DataObject resolveParent(String dataObjectAppId) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    DataObject parent = dataObjectDAO.findByAppId(dataObjectAppId);
    if (parent == null || parent.getCollection() == null) {
      throw new NotFoundException("No DataObject with appId " + dataObjectAppId);
    }
    return parent;
  }

  private static String asString(Object v) {
    return v == null ? null : v.toString();
  }
}
