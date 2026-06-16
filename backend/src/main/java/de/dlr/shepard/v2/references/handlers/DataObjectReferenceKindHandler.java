package de.dlr.shepard.v2.references.handlers;

import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
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
 * duplicated. Payload key set: {@code {referencedDataObjectAppId, relationship}}.
 *
 * <p>Pre-feature rows whose {@code referencedDataObject} carries no {@code appId}
 * will resolve {@code referencedDataObjectAppId} as {@code null} in the IO —
 * consistent with the "return null, never backfill" contract for pre-feature rows.
 */
@RequestScoped
public class DataObjectReferenceKindHandler implements ReferenceKindHandler {

  @Inject
  DataObjectReferenceService dataObjectReferenceService;

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
    DataObject referencedDataObject = ref.getReferencedDataObject();
    io.put(
      "referencedDataObjectAppId",
      referencedDataObject != null ? referencedDataObject.getAppId() : null
    );
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
    DataObject parent = resolveParent(dataObjectAppId);
    List<DataObjectReference> refs = dataObjectReferenceService.getAllReferencesByDataObjectId(
      parent.getCollection().getShepardId(),
      parent.getShepardId(),
      null
    );
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
