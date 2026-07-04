package de.dlr.shepard.v2.spatial.promote;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.context.references.spatialdata.daos.SpatialDataReferenceDAO;
import de.dlr.shepard.context.references.spatialdata.entities.SpatialDataReference;
import de.dlr.shepard.data.spatialdata.daos.SpatialDataContainerDAO;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

/**
 * SPATIAL-UNIFY-004 — the in-context "Promote to spatial" worker.
 *
 * <p>Given an eligible singleton {@link FileReference} (pointcloud / trajectory),
 * mints a {@link SpatialDataContainer} + a {@link SpatialDataReference} bound to
 * it on the same DataObject and marks the container {@code promotionState=pending}
 * so the Python {@code spatial-importer} sidecar can stream the points in. The
 * container is an implementation detail behind the reference (aidocs/integrations/124 §3.2);
 * the user addresses the reference appId.
 *
 * <p><b>Idempotent</b>: re-promoting the same FileReference resolves the existing
 * spatial reference (keyed via {@code SpatialDataContainer.sourceFileReferenceAppId})
 * instead of minting a duplicate.
 *
 * <p>The promote endpoint is a mutating 2xx request under {@code /v2/}, so the
 * core {@code ProvenanceCaptureFilter} records the typed {@code :Activity}
 * automatically (CLAUDE.md "admin/mutating endpoints capture by default";
 * secondary-write is fire-and-forget). No explicit {@code ProvenanceService}
 * wiring is needed in the plugin.
 */
@RequestScoped
public class SpatialPromoteService {

  @Inject
  SingletonFileReferenceService singletonFileReferenceService;

  @Inject
  SpatialDataContainerDAO spatialDataContainerDAO;

  @Inject
  SpatialDataReferenceDAO spatialDataReferenceDAO;

  @Inject
  de.dlr.shepard.auth.permission.services.PermissionsService permissionsService;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  /**
   * Promote an eligible FileReference into a SpatialDataReference (minting a
   * SpatialDataContainer behind it), idempotently.
   *
   * @param fileReferenceAppId the source singleton FileReference's appId
   * @return the unified IO of the spatial reference (existing or newly minted),
   *   plus a {@code created} flag in the payload for the resource to set 200/201
   */
  public PromoteResult promote(String fileReferenceAppId) {
    if (fileReferenceAppId == null || fileReferenceAppId.isBlank()) {
      throw new BadRequestException("fileReferenceAppId is required");
    }
    FileReference fileRef = singletonFileReferenceService.getByAppId(fileReferenceAppId);
    if (fileRef == null || fileRef.isDeleted()) {
      throw new NotFoundException("No FileReference with appId " + fileReferenceAppId);
    }
    DataObject parent = fileRef.getDataObject();
    if (parent == null) {
      throw new NotFoundException("FileReference " + fileReferenceAppId + " has no parent DataObject");
    }

    // Eligibility: classify by attached filename, falling back to the reference name.
    String filename = fileRef.getFile() != null ? fileRef.getFile().getFilename() : null;
    boolean eligible =
      SpatialFileClassifier.isEligible(filename) || SpatialFileClassifier.isEligible(fileRef.getName());
    if (!eligible) {
      throw new BadRequestException(
        "FileReference " + fileReferenceAppId + " is not an eligible spatial payload " +
        "(expected a pointcloud/trajectory: .las/.laz/.ply/.e57/.pcd/.xyz/.pts or a " +
        "named pointcloud/trajectory file)"
      );
    }

    // Idempotency: an existing container minted from this same file → return its reference.
    SpatialDataContainer existing = spatialDataContainerDAO.findBySourceFileReferenceAppId(fileReferenceAppId);
    if (existing != null) {
      SpatialDataReference existingRef = findReferenceForContainer(existing, parent);
      if (existingRef != null) {
        Log.debugf(
          "SPATIAL-UNIFY-004: re-promote of FileReference %s resolved existing spatial ref %s",
          fileReferenceAppId,
          existingRef.getAppId()
        );
        return new PromoteResult(toIO(existingRef), false);
      }
    }

    User user = userService.getCurrentUser();
    String baseName = filename != null && !filename.isBlank() ? filename : fileRef.getName();

    // 1) Mint the container behind the reference, marked promotion-pending.
    SpatialDataContainer container = new SpatialDataContainer();
    container.setCreatedAt(dateHelper.getDate());
    container.setCreatedBy(user);
    container.setName(baseName);
    container.setSourceFileReferenceAppId(fileReferenceAppId);
    container.setPromotionState("pending");
    container = spatialDataContainerDAO.createOrUpdate(container);
    permissionsService.createPermissions(container, user, PermissionType.Private);

    // 2) Mint the SpatialDataReference bound to it.
    SpatialDataReference ref = new SpatialDataReference();
    ref.setCreatedAt(dateHelper.getDate());
    ref.setCreatedBy(user);
    ref.setDataObject(parent);
    ref.setName(baseName);
    ref.setSpatialDataContainer(container);
    ref = spatialDataReferenceDAO.createOrUpdate(ref);
    ref.setShepardId(ref.getId());
    ref = spatialDataReferenceDAO.createOrUpdate(ref);

    Log.infof(
      "SPATIAL-UNIFY-004: promoted FileReference %s → spatial ref %s (container %s, pending import)",
      fileReferenceAppId,
      ref.getAppId(),
      container.getAppId()
    );
    return new PromoteResult(toIO(ref), true);
  }

  private SpatialDataReference findReferenceForContainer(SpatialDataContainer container, DataObject parent) {
    for (SpatialDataReference r : spatialDataReferenceDAO.findByDataObjectAppId(parent.getAppId())) {
      if (
        r.getSpatialDataContainer() != null &&
        r.getSpatialDataContainer().getId() != null &&
        r.getSpatialDataContainer().getId().equals(container.getId())
      ) {
        return r;
      }
    }
    return null;
  }

  private ReferenceV2IO toIO(SpatialDataReference ref) {
    ReferenceV2IO io = new ReferenceV2IO(ref, "spatial");
    io.put("geometryFilter", ref.getGeometryFilter());
    io.put("measurementsFilter", ref.getMeasurementsFilter());
    io.put("startTime", ref.getStartTime());
    io.put("endTime", ref.getEndTime());
    io.put("metadata", ref.getMetadata());
    io.put("limit", ref.getLimit());
    io.put("skip", ref.getSkip());
    SpatialDataContainer c = ref.getSpatialDataContainer();
    io.put("spatialDataContainerAppId", c == null ? null : c.getAppId());
    io.put("promotionState", c == null ? null : c.getPromotionState());
    return io;
  }

  /** Result wrapper: the unified IO + whether a new spatial reference was minted. */
  public record PromoteResult(ReferenceV2IO io, boolean created) {}
}
