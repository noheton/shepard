package de.dlr.shepard.v2.references.handlers;

import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.dataobject.daos.DataObjectReferenceDAO;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import de.dlr.shepard.context.references.dataobject.io.DataObjectReferenceIO;
import de.dlr.shepard.context.references.dataobject.services.DataObjectReferenceService;
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
 * V2-SWEEP-004-1 — in-tree {@link ReferenceKindHandler} for {@code kind=dataobject}.
 * Delegates wholly to the existing {@link DataObjectReferenceService}; no logic is
 * duplicated.
 *
 * <p>Payload key set: {@code {referencedDataObjectAppId, referencedDataObjectName,
 * referencedCollectionAppId, referencedCollectionName, relationship}}.
 * The name/collection fields are resolved by re-fetching the referenced DataObject
 * at {@code DEPTH_ENTITY=1} so the frontend can build appId-routed links without an
 * extra round-trip (MISSING-V2-APPID-IN-REFLISTS slice 3). Pre-feature rows whose
 * {@code referencedDataObject} carries no {@code appId} resolve those fields as
 * {@code null} — consistent with the "return null, never backfill" contract.
 */
@RequestScoped
public class DataObjectReferenceKindHandler implements ReferenceKindHandler {

  @Inject
  DataObjectReferenceService dataObjectReferenceService;

  @Inject
  DataObjectReferenceDAO dataObjectReferenceDAO;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Override
  public String kind() {
    return "dataobject";
  }

  @Override
  public boolean owns(BasicReference reference) {
    return reference instanceof DataObjectReference;
  }

  @Override
  public BasicReference findByAppId(String appId) {
    return dataObjectReferenceService.findByAppId(appId);
  }

  @Override
  public ReferenceV2IO toIO(BasicReference reference) {
    DataObjectReference ref = (DataObjectReference) reference;
    ReferenceV2IO io = new ReferenceV2IO(ref, kind());
    DataObject stub = ref.getReferencedDataObject();
    String refDoAppId = stub != null ? stub.getAppId() : null;
    io.put("referencedDataObjectAppId", refDoAppId);

    // MISSING-V2-APPID-IN-REFLISTS slice 3: re-fetch at depth=1 to get name +
    // collection so clients can build appId-routed links without a second call.
    if (refDoAppId != null) {
      DataObject full = dataObjectDAO.findByAppId(refDoAppId);
      if (full != null) {
        io.put("referencedDataObjectName", full.getName());
        Collection refColl = full.getCollection();
        io.put("referencedCollectionAppId", refColl != null ? refColl.getAppId() : null);
        io.put("referencedCollectionName", refColl != null ? refColl.getName() : null);
      }
    }

    io.put("relationship", ref.getRelationship());
    return io;
  }

  @Override
  public ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body) {
    if (body == null) throw new BadRequestException("create body is required for kind=dataobject");
    DataObject parent = resolveParent(dataObjectAppId);

    Object rawAppId = body.get("referencedDataObjectAppId");
    if (rawAppId == null) {
      throw new BadRequestException("referencedDataObjectAppId is required for kind=dataobject");
    }
    DataObject referenced = dataObjectDAO.findByAppId(rawAppId.toString());
    if (referenced == null) {
      throw new NotFoundException("No DataObject with appId " + rawAppId);
    }

    DataObjectReferenceIO ioIn = new DataObjectReferenceIO();
    ioIn.setName(asString(body.get("name")));
    ioIn.setReferencedDataObjectId(referenced.getShepardId());
    ioIn.setRelationship(asString(body.get("relationship")));

    DataObjectReference created = dataObjectReferenceService.createReference(
      parent.getCollection().getShepardId(),
      parent.getShepardId(),
      ioIn
    );
    return toIO(created);
  }

  @Override
  public ReferenceV2IO patch(String appId, Map<String, Object> patch) {
    DataObjectReference updated = dataObjectReferenceService.patchReferenceByAppId(appId, patch);
    return toIO(updated);
  }

  @Override
  public void delete(String appId) {
    DataObjectReference ref = dataObjectReferenceService.findByAppId(appId);
    if (ref == null) throw new NotFoundException("No DataObjectReference with appId " + appId);
    DataObject parent = ref.getDataObject();
    if (parent == null || parent.getCollection() == null) {
      throw new NotFoundException("DataObjectReference " + appId + " has no resolvable parent DataObject");
    }
    dataObjectReferenceService.deleteReference(
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
    List<DataObjectReference> refs = dataObjectReferenceDAO.findByDataObjectAppId(dataObjectAppId);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (DataObjectReference ref : refs) {
      if (ref != null && !ref.isDeleted()) out.add(toIO(ref));
    }
    return out;
  }

  @Override
  public int countByDataObject(String dataObjectAppId, String subKind) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    return dataObjectReferenceDAO.countByDataObjectAppId(dataObjectAppId);
  }

  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind, int skip, int limit) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    List<DataObjectReference> refs = dataObjectReferenceDAO.findByDataObjectAppId(dataObjectAppId, skip, limit);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (DataObjectReference ref : refs) {
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
