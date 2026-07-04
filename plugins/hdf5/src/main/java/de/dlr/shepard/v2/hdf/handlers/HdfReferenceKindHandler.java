package de.dlr.shepard.v2.hdf.handlers;

import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.data.hdf.daos.HdfReferenceDAO;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import de.dlr.shepard.data.hdf.entities.HdfReference;
import de.dlr.shepard.data.hdf.io.HdfReferenceRequestIO;
import de.dlr.shepard.data.hdf.services.HdfReferenceService;
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
 * PLUGIN-REF-HANDLER-HDF — {@link ReferenceKindHandler} for {@code kind=hdf}.
 *
 * <p>Discovered via CDI {@code @Any Instance<ReferenceKindHandler>} by the
 * {@code ReferencesV2Service} dispatcher. Delegates CRUD to the existing
 * {@link HdfReferenceService} and {@link HdfReferenceDAO}.
 *
 * <p>Payload key set: {@code hdfContainerAppId, datasetPath, description}.
 *
 * <p>Mutable fields via PATCH: {@code name, description} only —
 * {@code hdfContainerAppId} and {@code datasetPath} are immutable after
 * creation (per A5c design).
 */
@RequestScoped
public class HdfReferenceKindHandler implements ReferenceKindHandler {

  @Inject
  HdfReferenceService hdfReferenceService;

  @Inject
  HdfReferenceDAO hdfReferenceDAO;

  @Inject
  UserService userService;

  @Override
  public String kind() {
    return "hdf";
  }

  @Override
  public boolean owns(BasicReference reference) {
    return reference instanceof HdfReference;
  }

  @Override
  public BasicReference findByAppId(String appId) {
    return hdfReferenceDAO.findByAppId(appId);
  }

  @Override
  public ReferenceV2IO toIO(BasicReference reference) {
    HdfReference ref = (HdfReference) reference;
    ReferenceV2IO io = new ReferenceV2IO(ref, kind());
    HdfContainer container = ref.getHdfContainer();
    io.put("hdfContainerAppId", container != null ? container.getAppId() : null);
    io.put("datasetPath", ref.getDatasetPath());
    io.put("description", ref.getDescription());
    return io;
  }

  @Override
  public ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body) {
    if (body == null) throw new BadRequestException("create body is required for kind=hdf");
    String hdfContainerAppId = asString(body.get("hdfContainerAppId"));
    String datasetPath = asString(body.get("datasetPath"));
    if (hdfContainerAppId == null || hdfContainerAppId.isBlank()) {
      throw new BadRequestException("hdfContainerAppId is required");
    }
    if (datasetPath == null || datasetPath.isBlank()) {
      throw new BadRequestException("datasetPath is required");
    }
    HdfReferenceRequestIO requestIO = new HdfReferenceRequestIO();
    requestIO.setHdfContainerAppId(hdfContainerAppId);
    requestIO.setDatasetPath(datasetPath);
    requestIO.setDescription(asString(body.get("description")));

    String caller = userService.getCurrentUser().getUsername();
    HdfReference created = hdfReferenceService.create(dataObjectAppId, requestIO, caller);
    return toIO(created);
  }

  @Override
  public ReferenceV2IO patch(String appId, Map<String, Object> patch) {
    HdfReference ref = hdfReferenceDAO.findByAppId(appId);
    if (ref == null) throw new NotFoundException("No HdfReference with appId " + appId);
    if (patch == null || patch.isEmpty()) return toIO(ref);

    boolean changed = false;
    if (patch.containsKey("name")) {
      Object v = patch.get("name");
      if (!(v instanceof String s) || s.isBlank()) {
        throw new BadRequestException("'name' must be a non-blank string");
      }
      if (!s.equals(ref.getName())) {
        ref.setName(s);
        changed = true;
      }
    }
    if (patch.containsKey("description")) {
      String v = asString(patch.get("description"));
      if (!java.util.Objects.equals(v, ref.getDescription())) {
        ref.setDescription(v);
        changed = true;
      }
    }
    if (changed) {
      ref = hdfReferenceDAO.createOrUpdate(ref);
    }
    return toIO(ref);
  }

  @Override
  public void delete(String appId) {
    HdfReference ref = hdfReferenceDAO.findByAppId(appId);
    if (ref == null) throw new NotFoundException("No HdfReference with appId " + appId);
    DataObject owner = ref.getDataObject();
    String dataObjectAppId = (owner != null) ? owner.getAppId() : null;
    if (dataObjectAppId == null) {
      throw new NotFoundException(
        "HdfReference " + appId + " has no resolvable parent DataObject"
      );
    }
    String caller = userService.getCurrentUser().getUsername();
    hdfReferenceService.delete(dataObjectAppId, appId, caller);
  }

  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    String caller = userService.getCurrentUser().getUsername();
    List<HdfReference> refs = hdfReferenceService.listForDataObject(dataObjectAppId, caller);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (HdfReference ref : refs) {
      if (ref != null) out.add(toIO(ref));
    }
    return out;
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private static String asString(Object v) {
    return v == null ? null : v.toString();
  }
}
