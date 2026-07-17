package de.dlr.shepard.v2.references.handlers;

import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.uri.daos.URIReferenceDAO;
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
  URIReferenceDAO uriReferenceDAO;

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
    // APISIMP-URI-COLL-DOREF-NONPAGED-APPID: use the appId-keyed DAO path directly rather
    // than resolving the DataObject numeric Neo4j id via resolveParent() + getAllReferencesByDataObjectId().
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    List<URIReference> refs = uriReferenceDAO.findByDataObjectAppId(dataObjectAppId);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (URIReference ref : refs) {
      if (ref != null && !ref.isDeleted()) out.add(toIO(ref));
    }
    return out;
  }

  /**
   * APISIMP-REFS-INMEM-PAGING — delegates COUNT to Neo4j rather than loading all rows.
   * URI references carry no sub-kind discriminator so {@code subKind} is ignored.
   */
  @Override
  public int countByDataObject(String dataObjectAppId, String subKind) {
    resolveParent(dataObjectAppId); // validates existence; throws NotFoundException if absent
    return uriReferenceService.countByDataObjectAppId(dataObjectAppId);
  }

  /**
   * APISIMP-REFS-INMEM-PAGING — pushes SKIP/LIMIT to Neo4j; never loads the full list
   * then subList-slices in Java.
   * URI references carry no sub-kind discriminator so {@code subKind} is ignored.
   */
  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind, int skip, int limit) {
    resolveParent(dataObjectAppId); // validates existence; throws NotFoundException if absent
    List<URIReference> refs = uriReferenceService.listByDataObjectAppId(dataObjectAppId, skip, limit);
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
