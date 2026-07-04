package de.dlr.shepard.v2.references.handlers;

import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import de.dlr.shepard.context.references.uri.io.URIReferenceIO;
import de.dlr.shepard.context.references.uri.services.URIReferenceService;
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
 * V2CONV-A2 — in-tree {@link ReferenceKindHandler} for {@code kind=uri}.
 * Delegates wholly to the existing {@link URIReferenceService}; no logic
 * is duplicated. Payload key set: {@code {uri, relationship}}.
 */
@RequestScoped
public class UriReferenceKindHandler implements ReferenceKindHandler {

  @Inject
  URIReferenceService uriReferenceService;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Override
  public String kind() {
    return "uri";
  }

  @Override
  public boolean owns(BasicReference reference) {
    return reference instanceof URIReference;
  }

  @Override
  public BasicReference findByAppId(String appId) {
    return uriReferenceService.findByAppId(appId);
  }

  @Override
  public ReferenceV2IO toIO(BasicReference reference) {
    URIReference ref = (URIReference) reference;
    ReferenceV2IO io = new ReferenceV2IO(ref, kind());
    io.put("uri", ref.getUri());
    io.put("relationship", ref.getRelationship());
    return io;
  }

  @Override
  public ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body) {
    if (body == null) throw new BadRequestException("create body is required for kind=uri");
    DataObject parent = resolveParent(dataObjectAppId);

    URIReferenceIO ioIn = new URIReferenceIO();
    ioIn.setName(asString(body.get("name")));
    ioIn.setUri(asString(body.get("uri")));
    ioIn.setRelationship(asString(body.get("relationship")));

    URIReference created = uriReferenceService.createReference(
      parent.getCollection().getShepardId(),
      parent.getShepardId(),
      ioIn
    );
    return toIO(created);
  }

  @Override
  public ReferenceV2IO patch(String appId, Map<String, Object> patch) {
    URIReference updated = uriReferenceService.patchReferenceByAppId(appId, patch);
    return toIO(updated);
  }

  @Override
  public void delete(String appId) {
    URIReference ref = uriReferenceService.findByAppId(appId);
    if (ref == null) throw new NotFoundException("No URIReference with appId " + appId);
    DataObject parent = ref.getDataObject();
    if (parent == null || parent.getCollection() == null) {
      throw new NotFoundException("URIReference " + appId + " has no resolvable parent DataObject");
    }
    uriReferenceService.deleteReference(
      parent.getCollection().getShepardId(),
      parent.getShepardId(),
      ref.getShepardId()
    );
  }

  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind) {
    DataObject parent = resolveParent(dataObjectAppId);
    List<URIReference> refs = uriReferenceService.getAllReferencesByDataObjectId(
      parent.getCollection().getShepardId(),
      parent.getShepardId(),
      null
    );
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (URIReference ref : refs) {
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
