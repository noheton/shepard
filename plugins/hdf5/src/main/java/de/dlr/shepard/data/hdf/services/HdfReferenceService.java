package de.dlr.shepard.data.hdf.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.data.hdf.daos.HdfContainerDAO;
import de.dlr.shepard.data.hdf.daos.HdfReferenceDAO;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import de.dlr.shepard.data.hdf.entities.HdfReference;
import de.dlr.shepard.data.hdf.io.HdfReferenceRequestIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;

/**
 * A5c — CRUD service for {@link HdfReference}.
 *
 * <p>Permission model: HdfReference inherits the DataObject's
 * Collection permissions. A caller with READ on the Collection may
 * list references; a caller with WRITE may create; DELETE requires
 * WRITE as well (mirrors the FileReference behaviour).
 *
 * <p>Annotation hookup (E6/AnnotatableHdfDataset) is deferred — see
 * {@code aidocs/16-dispatcher-backlog.md} row A5c-annotation.
 */
@RequestScoped
public class HdfReferenceService {

  @Inject
  HdfReferenceDAO hdfReferenceDAO;

  @Inject
  HdfContainerDAO hdfContainerDAO;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  PermissionsService permissionsService;

  /**
   * List all {@link HdfReference} nodes attached to the DataObject
   * identified by {@code dataObjectAppId}. Performs a READ permission
   * check on the parent Collection via
   * {@link PermissionsService#isAccessAllowedForDataObjectAppId}.
   *
   * @param dataObjectAppId UUID v7 of the DataObject.
   * @param caller          caller's username (from SecurityContext).
   * @return list of references; never {@code null}.
   * @throws NotFoundException if the DataObject does not exist.
   * @throws ForbiddenException if the caller lacks READ.
   */
  public List<HdfReference> listForDataObject(String dataObjectAppId, String caller) {
    assertDataObjectReadable(dataObjectAppId, caller);
    return hdfReferenceDAO.findByDataObjectAppId(dataObjectAppId);
  }

  /**
   * Create a new {@link HdfReference} attached to the DataObject
   * identified by {@code dataObjectAppId}.
   *
   * @param dataObjectAppId UUID v7 of the DataObject.
   * @param body            inbound wire shape; {@code hdfContainerAppId}
   *                        and {@code datasetPath} are required.
   * @param caller          caller's username.
   * @return the persisted reference including its server-minted appId.
   * @throws NotFoundException if the DataObject or target container
   *                              does not exist.
   * @throws ForbiddenException if the caller lacks WRITE on the parent
   *                              Collection.
   */
  public HdfReference create(String dataObjectAppId, HdfReferenceRequestIO body, String caller) {
    assertDataObjectWritable(dataObjectAppId, caller);

    DataObject dataObject = resolveDataObjectByAppId(dataObjectAppId);
    if (dataObject == null) {
      throw new NotFoundException(
        "DataObject with appId %s not found or deleted".formatted(dataObjectAppId)
      );
    }

    HdfContainer container = hdfContainerDAO.findByAppId(body.getHdfContainerAppId());
    if (container == null || container.isDeleted()) {
      throw new NotFoundException(
        "HdfContainer with appId %s not found or deleted".formatted(body.getHdfContainerAppId())
      );
    }

    HdfReference ref = new HdfReference();
    ref.setAppId(AppIdGenerator.next());
    ref.setDatasetPath(body.getDatasetPath());
    ref.setDescription(body.getDescription());
    ref.setHdfContainer(container);
    // Wire the back-link: DataObject → HdfReference via HAS_REFERENCE.
    // Setting both sides ensures OGM persists the edge in a single session.
    ref.setDataObject(dataObject);

    HdfReference saved = hdfReferenceDAO.createOrUpdate(ref);
    Log.infof(
      "A5c: created HdfReference appId=%s for DataObject appId=%s -> container appId=%s path=%s",
      saved.getAppId(), dataObjectAppId, container.getAppId(), body.getDatasetPath()
    );
    return saved;
  }

  /**
   * Delete the {@link HdfReference} identified by {@code referenceAppId},
   * enforcing that it belongs to the DataObject identified by
   * {@code dataObjectAppId} and that the caller has WRITE permission.
   *
   * @param dataObjectAppId UUID v7 of the owning DataObject.
   * @param referenceAppId  UUID v7 of the reference to delete.
   * @param caller          caller's username.
   * @throws NotFoundException if the reference is not found or does
   *                              not belong to the stated DataObject.
   * @throws ForbiddenException if the caller lacks WRITE.
   */
  public void delete(String dataObjectAppId, String referenceAppId, String caller) {
    assertDataObjectWritable(dataObjectAppId, caller);

    HdfReference ref = hdfReferenceDAO.findByAppId(referenceAppId);
    if (ref == null) {
      throw new NotFoundException(
        "HdfReference with appId %s not found".formatted(referenceAppId)
      );
    }
    // Verify ownership: the reference must belong to the stated DataObject.
    DataObject owner = ref.getDataObject();
    if (owner == null || !dataObjectAppId.equals(owner.getAppId())) {
      throw new NotFoundException(
        "HdfReference %s does not belong to DataObject %s".formatted(referenceAppId, dataObjectAppId)
      );
    }

    hdfReferenceDAO.deleteByNeo4jId(ref.getId());
    Log.infof(
      "A5c: deleted HdfReference appId=%s from DataObject appId=%s",
      referenceAppId, dataObjectAppId
    );
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  /**
   * Resolve DataObject by appId via the EntityIdResolver → DAO hop,
   * matching the pattern used by {@code SingletonFileReferenceService}.
   */
  private DataObject resolveDataObjectByAppId(String appId) {
    if (appId == null) return null;
    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(appId);
    } catch (NotFoundException e) {
      return null;
    }
    DataObject dataObject = dataObjectDAO.findByNeo4jId(ogmId);
    if (dataObject == null || dataObject.isDeleted()) {
      return null;
    }
    return dataObject;
  }

  // ─── permission guards ────────────────────────────────────────────────────

  private void assertDataObjectReadable(String dataObjectAppId, String caller) {
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObjectAppId, AccessType.Read, caller)) {
      throw new ForbiddenException(
        "Caller %s has no READ permission on DataObject %s".formatted(caller, dataObjectAppId)
      );
    }
  }

  private void assertDataObjectWritable(String dataObjectAppId, String caller) {
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObjectAppId, AccessType.Write, caller)) {
      throw new ForbiddenException(
        "Caller %s has no WRITE permission on DataObject %s".formatted(caller, dataObjectAppId)
      );
    }
  }
}
