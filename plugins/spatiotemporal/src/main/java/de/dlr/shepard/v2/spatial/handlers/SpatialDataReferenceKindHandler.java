package de.dlr.shepard.v2.spatial.handlers;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.spatialdata.daos.SpatialDataReferenceDAO;
import de.dlr.shepard.context.references.spatialdata.entities.SpatialDataReference;
import de.dlr.shepard.context.references.spatialdata.services.SpatialDataReferenceService;
import de.dlr.shepard.data.spatialdata.daos.SpatialDataContainerDAO;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
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
 * SPATIAL-UNIFY-002 — {@link ReferenceKindHandler} for {@code kind=spatial}.
 *
 * <p>Discovered via CDI {@code @Any Instance<ReferenceKindHandler>} by the
 * core {@code ReferencesV2Service} dispatcher. Delegates CRUD to the existing
 * {@link SpatialDataReferenceService} / {@link SpatialDataReferenceDAO} — no
 * new storage. The Video / Git handlers are the verbatim templates.
 *
 * <p>This is the surface fix from {@code aidocs/integrations/124}: spatial
 * data is a Reference like File / TimeSeries / Video, addressed by the
 * reference {@code appId}. The {@code SpatialDataContainer} behind the
 * reference stays a storage primitive (§3.2) — the {@code [:IS_IN_CONTAINER]}
 * edge already mirrors {@code FileReference → FileContainer}; users never pick
 * a container from a list.
 *
 * <p>Note the deliberate name split (§3.3): the {@code PayloadKind.name()}
 * registered by this plugin is {@code "spatiotemporal"} (the schema-registration
 * identity), while the <em>reference kind token</em> exposed on the wire / in
 * the UI tab is {@code "spatial"} — matching the operator's language. The same
 * split is fine for Video.
 *
 * <p>Payload key set: {@code geometryFilter, measurementsFilter, startTime,
 * endTime, metadata, limit, skip, spatialDataContainerAppId}.
 */
@RequestScoped
public class SpatialDataReferenceKindHandler implements ReferenceKindHandler {

  @Inject
  SpatialDataReferenceService spatialDataReferenceService;

  @Inject
  SpatialDataReferenceDAO spatialDataReferenceDAO;

  @Inject
  SpatialDataContainerDAO spatialDataContainerDAO;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Override
  public String kind() {
    return "spatial";
  }

  @Override
  public boolean owns(BasicReference reference) {
    return reference instanceof SpatialDataReference;
  }

  @Override
  public BasicReference findByAppId(String appId) {
    return spatialDataReferenceService.findByAppId(appId);
  }

  @Override
  public ReferenceV2IO toIO(BasicReference reference) {
    SpatialDataReference ref = (SpatialDataReference) reference;
    ReferenceV2IO io = new ReferenceV2IO(ref, kind());
    io.put("geometryFilter", ref.getGeometryFilter());
    io.put("measurementsFilter", ref.getMeasurementsFilter());
    io.put("startTime", ref.getStartTime());
    io.put("endTime", ref.getEndTime());
    io.put("metadata", ref.getMetadata());
    io.put("limit", ref.getLimit());
    io.put("skip", ref.getSkip());
    SpatialDataContainer container = ref.getSpatialDataContainer();
    // The container is an implementation detail behind the reference (§3.2):
    // expose its appId only so the viewer can resolve bytes by appId — never a
    // numeric id.
    io.put("spatialDataContainerAppId", container == null ? null : container.getAppId());
    return io;
  }

  /**
   * SPATIAL-UNIFY-002 — JSON create binds a new {@link SpatialDataReference} to
   * an <em>existing</em> {@link SpatialDataContainer} resolved by its
   * {@code appId} (the design's §3.1 JSON-create path). The in-context
   * "Promote to spatial" flow (SPATIAL-UNIFY-004) is the primary way a
   * container gets minted; this create path lets a caller window an existing
   * spatial container with a different geometry/measurement filter.
   */
  @Override
  public ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    if (body == null) {
      throw new BadRequestException("create body is required for kind=spatial");
    }
    String containerAppId = asString(body.get("spatialDataContainerAppId"));
    if (containerAppId == null || containerAppId.isBlank()) {
      throw new BadRequestException(
        "spatialDataContainerAppId is required — promote a file via " +
        "POST /v2/spatial/promote?fileReferenceAppId=… to mint a new spatial container"
      );
    }
    DataObject parent = dataObjectDAO.findByAppId(dataObjectAppId);
    if (parent == null) {
      throw new NotFoundException("No DataObject with appId " + dataObjectAppId);
    }
    SpatialDataContainer container = spatialDataContainerDAO.findByAppId(containerAppId);
    if (container == null) {
      throw new BadRequestException("No SpatialDataContainer with appId " + containerAppId);
    }

    User user = userService.getCurrentUser();
    SpatialDataReference toCreate = new SpatialDataReference();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDataObject(parent);
    String name = asString(body.get("name"));
    toCreate.setName(name != null && !name.isBlank() ? name : container.getName());
    toCreate.setGeometryFilter(asString(body.get("geometryFilter")));
    toCreate.setMeasurementsFilter(asString(body.get("measurementsFilter")));
    toCreate.setMetadata(asString(body.get("metadata")));
    toCreate.setStartTime(asLong(body.get("startTime")));
    toCreate.setEndTime(asLong(body.get("endTime")));
    toCreate.setLimit(asInteger(body.get("limit")));
    toCreate.setSkip(asInteger(body.get("skip")));
    toCreate.setSpatialDataContainer(container);

    SpatialDataReference created = spatialDataReferenceDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = spatialDataReferenceDAO.createOrUpdate(created);
    return toIO(created);
  }

  @Override
  public ReferenceV2IO patch(String appId, Map<String, Object> patch) {
    SpatialDataReference ref = spatialDataReferenceService.findByAppId(appId);
    if (ref == null) throw new NotFoundException("No SpatialDataReference with appId " + appId);
    boolean changed = false;
    if (patch != null && patch.containsKey("name")) {
      Object v = patch.get("name");
      if (!(v instanceof String s) || s.isBlank()) {
        throw new BadRequestException("'name' must be a non-blank string");
      }
      if (!s.equals(ref.getName())) {
        ref.setName(s);
        changed = true;
      }
    }
    if (changed) {
      User user = userService.getCurrentUser();
      ref.setUpdatedAt(dateHelper.getDate());
      ref.setUpdatedBy(user);
      ref = spatialDataReferenceDAO.createOrUpdate(ref);
    }
    return toIO(ref);
  }

  @Override
  public void delete(String appId) {
    SpatialDataReference ref = spatialDataReferenceService.findByAppId(appId);
    if (ref == null) throw new NotFoundException("No SpatialDataReference with appId " + appId);
    spatialDataReferenceService.delete(ref);
  }

  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    List<SpatialDataReference> refs = spatialDataReferenceService.listByDataObjectAppId(dataObjectAppId);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (SpatialDataReference ref : refs) {
      if (ref != null && !ref.isDeleted()) out.add(toIO(ref));
    }
    return out;
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private static String asString(Object v) {
    return v == null ? null : v.toString();
  }

  private static Long asLong(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return n.longValue();
    try {
      return Long.valueOf(v.toString());
    } catch (NumberFormatException nfe) {
      throw new BadRequestException("expected a numeric value but got: " + v);
    }
  }

  private static Integer asInteger(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return n.intValue();
    try {
      return Integer.valueOf(v.toString());
    } catch (NumberFormatException nfe) {
      throw new BadRequestException("expected an integer value but got: " + v);
    }
  }
}
