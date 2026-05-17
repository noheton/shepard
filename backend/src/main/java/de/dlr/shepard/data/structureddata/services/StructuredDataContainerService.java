package de.dlr.shepard.data.structureddata.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.data.AbstractContainerService;
import de.dlr.shepard.data.file.daos.PayloadVersionDAO;
import de.dlr.shepard.data.file.entities.PayloadVersion;
import de.dlr.shepard.data.structureddata.daos.StructuredDataContainerDAO;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import de.dlr.shepard.data.structureddata.io.StructuredDataContainerIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;

@RequestScoped
public class StructuredDataContainerService
  extends AbstractContainerService<StructuredDataContainer, StructuredDataContainerIO> {

  @Inject
  StructuredDataContainerDAO structuredDataContainerDAO;

  @Inject
  StructuredDataService structuredDataService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Inject
  PayloadVersionDAO payloadVersionDAO;

  /**
   * Creates a StructuredDataContainer and stores it in Neo4J
   *
   * @param structuredDataContainerIO to be stored
   * @param username                  of the related user
   * @return the created StructuredDataContainer
   */
  @Override
  public StructuredDataContainer createContainer(StructuredDataContainerIO structuredDataContainerIO) {
    User user = userService.getCurrentUser();
    String mongoId = structuredDataService.createStructuredDataContainer();

    var toCreate = new StructuredDataContainer();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setMongoId(mongoId);
    toCreate.setName(structuredDataContainerIO.getName());

    var created = structuredDataContainerDAO.createOrUpdate(toCreate);
    permissionsService.createPermissions(created, user, PermissionType.Private);
    return created;
  }

  /**
   * Searches the StructuredDataContainer in Neo4j
   *
   * @param id identifies the searched StructuredDataContainer
   * @return the StructuredDataContainer with matching id or null
   */
  @Override
  public StructuredDataContainer getContainer(long id) {
    StructuredDataContainer structuredDataContainer = structuredDataContainerDAO.findByNeo4jId(id);
    if (structuredDataContainer == null || structuredDataContainer.isDeleted()) {
      String errorMsg = "ID ERROR - Structured Data Container with id %s is null or deleted".formatted(id);
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }
    assertIsAllowedToReadContainer(id);
    return structuredDataContainer;
  }

  /**
   * Searches the database for all StructuredDataContainers
   *
   * @param params   QueryParamsHelper
   * @param username the name of the user
   * @return a list of StructuredDataContainers
   */
  @Override
  public List<StructuredDataContainer> getAllContainers(QueryParamHelper params) {
    User user = userService.getCurrentUser();
    var containers = structuredDataContainerDAO.findAllStructuredDataContainers(params, user.getUsername());
    return containers;
  }

  /**
   * Deletes a StructuredDataContainer in Neo4j
   *
   * @param structuredDataId identifies the StructuredDataContainer
   * @param username         identifies the deleting user
   * @return a boolean to determine if StructuredDataContainer was successfully
   *         deleted
   * @throws InvalidRequestException If StructuredDataContainer could not be deleted
   */
  @Override
  public void deleteContainer(long structuredDataId) {
    User user = userService.getCurrentUser();
    StructuredDataContainer structuredDataContainer = getContainer(structuredDataId);
    assertIsAllowedToDeleteContainer(structuredDataId);

    String mongoId = structuredDataContainer.getMongoId();
    structuredDataContainer.setDeleted(true);
    structuredDataContainer.setUpdatedAt(dateHelper.getDate());
    structuredDataContainer.setUpdatedBy(user);
    structuredDataContainerDAO.createOrUpdate(structuredDataContainer);

    structuredDataService.deleteStructuredDataContainer(mongoId);
  }

  /**
   * Upload structured data
   *
   * @param structuredDataContainerID identifies the container
   * @param payload                   the payload to upload
   * @return StructuredData with the new oid
   */
  public StructuredData createStructuredData(long structuredDataContainerID, StructuredDataPayload payload) {
    StructuredDataContainer structuredDataContainer = getContainer(structuredDataContainerID);
    assertIsAllowedToEditContainer(structuredDataContainerID);

    StructuredData result = structuredDataService.createStructuredData(structuredDataContainer.getMongoId(), payload);

    structuredDataContainer.addStructuredData(result);
    structuredDataContainerDAO.createOrUpdate(structuredDataContainer);

    // PV1b: record a PayloadVersion node for this structured-data upload.
    // Best-effort: failure here does not roll back the already-persisted document.
    recordPayloadVersion(structuredDataContainer, payload, result);

    return result;
  }

  /**
   * PV1b — mint and persist a {@link PayloadVersion} node for a successful
   * structured-data upload.
   *
   * <p>The {@code originalName} is the structured-data entry's name
   * ({@link StructuredData#getName()}); when the name is null (anonymous
   * document) the Mongo oid is used as fallback so the version node is
   * never blank. The version number is scoped to
   * {@code (containerAppId, originalName)} — matching the PV1a shape —
   * so repeated uploads of the same named entry build a version list.
   *
   * <p>The operation is best-effort: a failure logs a warning but does not
   * roll back the upload because the document is already persisted in Mongo
   * and the container graph is already updated.
   *
   * @param container the owning StructuredDataContainer (for its appId).
   * @param payload   the uploaded payload (for SHA-256 and sizeBytes computation).
   * @param result    the just-persisted StructuredData (for its oid).
   */
  private void recordPayloadVersion(
    StructuredDataContainer container,
    StructuredDataPayload payload,
    StructuredData result
  ) {
    if (container.getAppId() == null) {
      Log.warnf(
        "PV1b: container id=%d has no appId — skipping PayloadVersion recording for structured data '%s'",
        container.getId(),
        result != null ? result.getOid() : "null"
      );
      return;
    }
    try {
      User caller = userService.getCurrentUser();
      String callerName = (caller != null) ? caller.getUsername() : "unknown";
      String containerAppId = container.getAppId();

      // Determine the originalName: use the StructuredData name when present,
      // fall back to the Mongo oid so the scope key is never null.
      String entryName = (result != null && result.getName() != null && !result.getName().isBlank())
        ? result.getName()
        : (result != null ? result.getOid() : "unknown");

      // Compute SHA-256 and sizeBytes from the JSON payload bytes.
      String sha256 = null;
      Long sizeBytes = null;
      if (payload != null && payload.getPayload() != null) {
        byte[] payloadBytes = payload.getPayload().getBytes(StandardCharsets.UTF_8);
        sizeBytes = (long) payloadBytes.length;
        try {
          MessageDigest md = MessageDigest.getInstance("SHA-256");
          byte[] digest = md.digest(payloadBytes);
          StringBuilder sb = new StringBuilder(64);
          for (byte b : digest) {
            sb.append(String.format("%02X", b));
          }
          sha256 = sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
          // SHA-256 is always available in Java SE; log and continue without hash.
          Log.warnf("PV1b: SHA-256 not available: %s", e.getMessage());
        }
      }

      long nextVersion = payloadVersionDAO.findMaxVersionNumber(containerAppId, entryName) + 1;

      PayloadVersion pv = new PayloadVersion();
      pv.setContainerAppId(containerAppId);
      pv.setOriginalName(entryName);
      pv.setFileOid(result != null ? result.getOid() : null);
      pv.setSha256(sha256);
      pv.setSizeBytes(sizeBytes);
      pv.setVersionNumber(nextVersion);
      pv.setUploadedBy(callerName);
      pv.setUploadedAt(Instant.now().toString());
      payloadVersionDAO.createOrUpdate(pv);
      Log.infof(
        "PV1b: recorded PayloadVersion v%d for container=%s name='%s' sha256=%s",
        nextVersion, containerAppId, entryName, sha256 != null ? sha256.substring(0, 8) + "…" : "null"
      );
    } catch (Exception e) {
      // Version recording is best-effort; do not fail the upload.
      Log.warnf(
        "PV1b: failed to record PayloadVersion for container=%s: %s",
        container.getAppId(), e.getMessage()
      );
    }
  }

  /**
   * Get uploaded structured data
   *
   * @param structuredDataContainerID identifies the container
   * @param oid                       identifies the structured data within the
   *                                  container
   * @return StructuredDataPayload
   */
  public StructuredDataPayload getStructuredData(long structuredDataContainerID, String oid) {
    StructuredDataContainer structuredDataContainer = getContainer(structuredDataContainerID);

    return structuredDataService.getPayload(structuredDataContainer.getMongoId(), oid);
  }

  /**
   * Delete one single structured data object
   *
   * @param structuredDataContainerID identifies the container
   * @param oid                       identifies the structured data within the
   *                                  container
   */
  public void deleteStructuredData(long structuredDataContainerID, String oid) {
    StructuredDataContainer structuredDataContainer = getContainer(structuredDataContainerID);
    assertIsAllowedToEditContainer(structuredDataContainerID);

    structuredDataService.deletePayload(structuredDataContainer.getMongoId(), oid);

    List<StructuredData> newStructuredDatas = structuredDataContainer
      .getStructuredDatas()
      .stream()
      .filter(f -> !f.getOid().equals(oid))
      .toList();
    structuredDataContainer.setStructuredDatas(newStructuredDatas);
    structuredDataContainerDAO.createOrUpdate(structuredDataContainer);
  }

  /**
   * CC1b — look up a StructuredDataContainer by its appId with a read-permission
   * check. Used by the linked-data-objects REST endpoint.
   *
   * @throws InvalidPathException if no container with that appId exists
   */
  public StructuredDataContainer getContainerByAppId(String appId) {
    StructuredDataContainer c = structuredDataContainerDAO.findByAppId(appId)
      .orElseThrow(() -> new InvalidPathException(
        "StructuredDataContainer with appId '" + appId + "' not found"));
    if (c.isDeleted()) {
      throw new InvalidPathException("StructuredDataContainer '" + appId + "' is deleted");
    }
    assertIsAllowedToReadContainer(c.getId());
    return c;
  }

  /**
   * CC1b — return the list of non-deleted DataObjects that reference this
   * StructuredDataContainer via a StructuredDataReference.
   *
   * @param containerId numeric OGM id of the StructuredDataContainer
   * @return distinct DataObjects linked to this container
   */
  public List<DataObject> findLinkedDataObjectsById(long containerId) {
    StructuredDataContainer container = getContainer(containerId);
    String appId = container.getAppId();
    if (appId == null) {
      return java.util.Collections.emptyList();
    }
    return structuredDataContainerDAO.findLinkedDataObjectsByContainerAppId(appId);
  }
}
