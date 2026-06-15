package de.dlr.shepard.context.collection.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.configuration.feature.runtime.FeatureToggleRegistry;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.dataobject.daos.DataObjectReferenceDAO;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import de.dlr.shepard.context.semantic.services.AttributeAnnotationDualWriteService;
import de.dlr.shepard.context.version.services.VersionService;
import de.dlr.shepard.v2.collectionwatchers.services.CollectionWatcherService;
import de.dlr.shepard.v2.dataobject.io.CreateDataObjectV2IO;
import de.dlr.shepard.v2.dataobject.io.TypedPredecessorIO;
import de.dlr.shepard.v2.events.CollectionEventProducer;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RequestScoped
public class DataObjectService {

  /** PROV1k — shared ObjectMapper for serialising/deserialising typedPredecessorsJson. */
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  DataObjectReferenceDAO dataObjectReferenceDAO;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Inject
  VersionService versionService;

  @Inject
  CollectionService collectionService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  CollectionWatcherService collectionWatcherService;

  @Inject
  AttributeAnnotationDualWriteService attributeAnnotationDualWriteService;

  @Inject
  CollectionEventProducer collectionEventProducer;

  @Inject
  FeatureToggleRegistry featureToggleRegistry;

  @Inject
  ArchiveStateGuard archiveStateGuard;

  /**
   * Creates a DataObject
   *
   * @param collectionShepardId identifies the Collection
   * @param dataObject          to be stored
   * @return the stored DataObject with the auto generated id
   * @throws InvalidPathException if collection with collectionShepardId does not
   *                              exist
   * @throws InvalidPathException if collection with collectionShepardId does not
   *                              exist
   * @throws InvalidBodyException if the list of successors is not null or not empty
   */
  public DataObject createDataObject(long collectionShepardId, DataObjectIO dataObject) throws InvalidBodyException {
    Collection collection = collectionService.getCollection(collectionShepardId);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    // #27-ARCHIVED-02: 409 when the parent Collection is frozen.
    archiveStateGuard.assertCollectionNotArchived(collectionShepardId);

    User user = userService.getCurrentUser();

    // MFG5: enforce closed-enum status on create.
    StatusTransitionGuard.validateOnCreate(dataObject.getStatus());
    // MFG1: enforce quality-engineer role for quality-restricted statuses.
    boolean hasQualityRole = authenticationContext.getPrincipal() != null
      && authenticationContext.getPrincipal().hasRole(Constants.QUALITY_ENGINEER_ROLE);
    StatusTransitionGuard.validateQualityRole(dataObject.getStatus(), hasQualityRole);

    DataObject parent = findRelatedDataObject(collection.getShepardId(), dataObject.getParentId(), null);
    if (
      dataObject.getSuccessorIds() != null && dataObject.getSuccessorIds().length != 0
    ) throw new InvalidBodyException(
      "when creating a new dataObject the list of successors must not be specified or be empty"
    );
    // PROV1k: resolve typed predecessors from v2 create body (overrides predecessorIds when present).
    // The instanceof guard keeps the v1 create path completely unaffected.
    List<TypedPredecessorIO> typedPredecessors = null;
    if (dataObject instanceof CreateDataObjectV2IO v2ioForTyped) {
      typedPredecessors = v2ioForTyped.getTypedPredecessors();
    }

    List<DataObject> predecessors;
    String typedPredecessorsJson = null;
    if (typedPredecessors != null && !typedPredecessors.isEmpty()) {
      // Validate each entry and resolve to DataObject instances.
      for (TypedPredecessorIO tp : typedPredecessors) {
        tp.validate();
      }
      predecessors = resolveTypedPredecessors(collection.getShepardId(), typedPredecessors, null);
      typedPredecessorsJson = serialiseTypedPredecessors(typedPredecessors);
    } else {
      predecessors = findRelatedDataObjects(
        collection.getShepardId(),
        dataObject.getPredecessorIds(),
        null
      );
    }

    // MFG2: block creation if any predecessor is in a blocking quality status (feature-flagged).
    boolean qualityGatesEnabled = featureToggleRegistry.get("manufacturing-quality-gates")
      .map(t -> t.isEnabled())
      .orElse(false);
    if (qualityGatesEnabled && predecessors != null) {
      for (DataObject pred : predecessors) {
        String predStatus = pred.getStatus();
        if ("NCR_OPEN".equals(predStatus) || "ON_HOLD".equals(predStatus)) {
          throw new jakarta.ws.rs.WebApplicationException(
            "Predecessor " + (pred.getAppId() != null ? pred.getAppId() : pred.getShepardId()) +
            " is in status " + predStatus + " — resolve before creating a successor",
            jakarta.ws.rs.core.Response.Status.CONFLICT
          );
        }
      }
    }

    DataObject toCreate = new DataObject();
    toCreate.setAttributes(dataObject.getAttributes());
    toCreate.setDescription(dataObject.getDescription());
    toCreate.setStatus(dataObject.getStatus());
    toCreate.setName(dataObject.getName());
    toCreate.setCollection(collection);
    toCreate.setParent(parent);
    toCreate.setPredecessors(predecessors);
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    // LIC1 (FAIR-1): copy license + accessRights on create. Both nullable.
    toCreate.setLicense(dataObject.getLicense());
    toCreate.setAccessRights(dataObject.getAccessRights());
    // FAIR3: copy embargoEndDate on create. Nullable; only meaningful when accessRights=EMBARGOED.
    toCreate.setEmbargoEndDate(dataObject.getEmbargoEndDate());
    // PROV1j (EU AI Act Art. 50): propagate provenanceMode from v2 create body when present.
    // The instanceof guard keeps the v1 create path unaffected (DataObjectIO has no such field).
    if (dataObject instanceof CreateDataObjectV2IO v2io) {
      toCreate.setProvenanceMode(v2io.getProvenanceMode());
    }
    // PROV1k: store serialised typed predecessors JSON (null when only legacy predecessorIds used).
    toCreate.setTypedPredecessorsJson(typedPredecessorsJson);
    // FAIR2: stamp createdByOrcid from User.orcid at creation time.
    // Best-effort: never block creation if ORCID lookup fails.
    try {
      User creator = userService.getCurrentUser();
      if (creator != null && creator.getOrcid() != null) {
        toCreate.setCreatedByOrcid(creator.getOrcid());
      }
    } catch (Exception e) {
      Log.warnf("FAIR2: failed to stamp createdByOrcid for %s: %s", user.getUsername(), e.getMessage());
    }
    DataObject created = dataObjectDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = dataObjectDAO.createOrUpdate(created);
    versionService.attachToVersionOfVersionableEntityAndReturnVersion(collectionShepardId, created.getShepardId());

    // NEO-AUDIT-004: write the time-bucketed Agent index (:User)-[:created_in_month {ym}]->(:DataObject).
    // Best-effort — logged and swallowed on failure so creation is never blocked.
    dataObjectDAO.writeCreatedInMonth(created);

    // CW1: notify collection watchers — best-effort, does not block creation.
    // Only notify for top-level DataObjects (no parent) to avoid flooding on
    // hierarchical ingestion workflows.
    if (toCreate.getParent() == null && collection.getAppId() != null) {
      collectionWatcherService.notifyWatchersOfNewDataObject(
        collection.getAppId(),
        collection.getName(),
        created.getName(),
        collection.getId()
      );
    }

    // P13: emit SSE change-feed event for all subscribers of this Collection.
    if (collection.getAppId() != null) {
      collectionEventProducer.dataObjectCreated(
        collection.getAppId(),
        created.getAppId(),
        user.getUsername()
      );
    }

    // TPL4: mirror legacy attributes as synthetic SemanticAnnotation nodes when toggle is on.
    attributeAnnotationDualWriteService.backfillFromAttributes(created);

    return created;
  }

  /**
   * Get DataObject
   *
   * @param shepardId identifies the searched dataObject
   * @return the DataObject with the given id
   * @throws InvalidPathException if the DataObject cannot be found
   * @throws InvalidAuthException if user does not have read permissions on the
   *                              data object's collection
   */
  public DataObject getDataObject(long shepardId) {
    return getDataObject(shepardId, null);
  }

  /**
   * Get DataObject
   *
   * @param shepardId  identifies the searched dataObject
   * @param versionUID the dataobject's version UUID
   * @return an Optional containing the DataObject with the given id
   * @throws InvalidPathException if DataObject (with version UUID) cannot be
   *                              found
   * @throws InvalidAuthException if user does not have read permissions on the
   *                              data object's collection
   */
  public DataObject getDataObject(long shepardId, UUID versionUID) {
    DataObject ret;
    String errorMsg;
    if (versionUID == null) {
      ret = dataObjectDAO.findByShepardId(shepardId);
      errorMsg = "DataObject with id %s is null or deleted".formatted(shepardId);
    } else {
      ret = dataObjectDAO.findByShepardId(shepardId, versionUID);
      errorMsg = "DataObject with id %s and versionUID %s is null or deleted".formatted(shepardId, versionUID);
    }
    if (ret == null || ret.isDeleted()) {
      Log.error(errorMsg);
      throw new InvalidPathException("ID ERROR - " + errorMsg);
    }

    collectionService.assertIsAllowedToReadCollection(ret.getCollection().getShepardId());
    cutDeleted(ret);

    HashSet<Long> incomingReferencesIdList = new HashSet<Long>();
    for (DataObjectReference reference : ret.getIncoming()) incomingReferencesIdList.add(reference.getId());
    List<DataObjectReference> completeIncomingReferences = new ArrayList<DataObjectReference>();
    for (Long id : incomingReferencesIdList) completeIncomingReferences.add(dataObjectReferenceDAO.findByNeo4jId(id));

    HashSet<Long> childrenIdList = new HashSet<Long>();
    for (DataObject child : ret.getChildren()) childrenIdList.add(child.getId());
    List<DataObject> completeChildren = new ArrayList<DataObject>();
    for (Long id : childrenIdList) completeChildren.add(dataObjectDAO.findByNeo4jId(id));

    HashSet<Long> predecessorsIdList = new HashSet<Long>();
    for (DataObject predecessor : ret.getPredecessors()) predecessorsIdList.add(predecessor.getId());
    List<DataObject> completePredecessors = new ArrayList<DataObject>();
    for (Long id : predecessorsIdList) completePredecessors.add(dataObjectDAO.findByNeo4jId(id));

    HashSet<Long> successorsIdList = new HashSet<Long>();
    for (DataObject successor : ret.getSuccessors()) successorsIdList.add(successor.getId());
    List<DataObject> completeSuccessors = new ArrayList<DataObject>();
    for (Long id : successorsIdList) completeSuccessors.add(dataObjectDAO.findByNeo4jId(id));

    ret.setChildren(completeChildren);
    ret.setIncoming(completeIncomingReferences);
    ret.setPredecessors(completePredecessors);
    ret.setSuccessors(completeSuccessors);
    if (ret.getParent() != null) ret.setParent(dataObjectDAO.findByNeo4jId(ret.getParent().getId()));
    return ret;
  }

  /**
   * Get DataObject
   *
   * @param collectionShepardId collection's shepardId
   * @param shepardId           identifies the searched dataObject
   * @return the DataObject with the given id
   * @throws InvalidPathException if dataobject or collection cannot be found or
   *                              the dataobject does not match the collection
   * @throws InvalidAuthException if user does not have read permissions on the
   *                              collection
   */
  public DataObject getDataObject(long collectionShepardId, long shepardId) {
    return getDataObject(collectionShepardId, shepardId, null);
  }

  /**
   * Get DataObject
   *
   * @param collectionShepardId collection's shepardId
   * @param shepardId           identifies the searched dataObject
   * @param versionUID          the DataObject's version UUID
   * @return the DataObject with the given id
   * @throws InvalidPathException if DataObject or collection cannot be found or
   *                              the DataObject does not match the collection
   * @throws InvalidAuthException if user does not have read permissions on the
   *                              collection
   */
  public DataObject getDataObject(long shepardCollectionId, long shepardId, UUID versionUID) {
    collectionService.getCollection(shepardCollectionId);

    DataObject dataObject;
    try {
      // This may throw a 403 if the data object is in a different collection for
      // which the user does not have permissions -> handle that exception
      // specifically
      dataObject = getDataObject(shepardId, versionUID);
    } catch (InvalidAuthException ex) {
      throw new InvalidPathException("ID ERROR - There is no association between collection and dataObject");
    }

    if (!dataObject.getCollection().getShepardId().equals(shepardCollectionId)) {
      throw new InvalidPathException("ID ERROR - There is no association between collection and dataObject");
    }
    return dataObject;
  }

  /**
   * Searches the database for DataObjects.
   *
   * @param collectionShepardId  identifies the collection
   * @param paramsWithShepardIds encapsulates possible parameters
   * @param versionUID           identifies the version
   * @return a List of DataObjects
   * @throws InvalidPathException if collection with collectionShepardId does not
   *                              exist
   * @throws InvalidAuthException if user does not have read permissions on the
   *                              collection
   */
  public List<DataObject> getAllDataObjectsByShepardIds(
    long collectionShepardId,
    QueryParamHelper paramsWithShepardIds,
    UUID versionUID
  ) {
    collectionService.getCollection(collectionShepardId, versionUID);

    var unfiltered = dataObjectDAO.findByCollectionByShepardIds(collectionShepardId, paramsWithShepardIds, versionUID);
    var dataObjects = unfiltered.stream().map(this::cutDeleted).toList();
    return dataObjects;
  }

  /**
   * Updates a DataObject with new attributes. Hereby only not null attributes
   * will replace the old attributes.
   *
   * @param collectionShepardId ShepardId of the collection the dataobject is
   *                            assigned to
   * @param dataObjectShepardId Identifies the dataObject
   * @param dataObject          DataObject entity for updating.
   * @return updated DataObject.
   * @throws InvalidPathException if dataObject cannot be found or collection with
   *                              collectionShepardId does not exist
   * @throws InvalidAuthException if user does not have read or write permissions
   *                              on the collection
   * @throws InvalidBodyException if the list of successors is not admitted
   */
  public DataObject updateDataObject(long collectionShepardId, long dataObjectShepardId, DataObjectIO dataObject) {
    DataObject old = getDataObject(collectionShepardId, dataObjectShepardId);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    // #27-ARCHIVED-02: 409 when the parent Collection is frozen (prune-only).
    archiveStateGuard.assertCollectionNotArchived(collectionShepardId);

    // MFG5: enforce closed-enum status and forbid downgrade transitions on update.
    StatusTransitionGuard.validateOnUpdate(old.getStatus(), dataObject.getStatus());
    // MFG1: enforce quality-engineer role for quality-restricted statuses on update.
    boolean hasQualityRoleOnUpdate = authenticationContext.getPrincipal() != null
      && authenticationContext.getPrincipal().hasRole(Constants.QUALITY_ENGINEER_ROLE);
    StatusTransitionGuard.validateQualityRole(dataObject.getStatus(), hasQualityRoleOnUpdate);

    User user = userService.getCurrentUser();

    if (old.getParent() != null) dataObjectDAO.deleteHasChildRelation(
      old.getParent().getShepardId(),
      old.getShepardId()
    );

    if (old.getPredecessors() != null) old
      .getPredecessors()
      .forEach(predecessor -> {
        dataObjectDAO.deleteHasSuccessorRelation(predecessor.getShepardId(), old.getShepardId());
      });

    if (dataObject.getSuccessorIds() != null) {
      Set<Long> givenSuccessorIds = Arrays.stream(dataObject.getSuccessorIds()).boxed().collect(Collectors.toSet());
      Set<Long> foundSuccessorIds = old.getSuccessors().stream().map(DataObject::getId).collect(Collectors.toSet());
      if (!givenSuccessorIds.equals(foundSuccessorIds)) throw new InvalidBodyException(
        "the given list of successors does not match the current list of successors"
      );
    }
    dataObjectDAO.deleteAllAttributes(old);

    DataObject newParent = findRelatedDataObject(
      old.getCollection().getShepardId(),
      dataObject.getParentId(),
      dataObjectShepardId
    );

    // PROV1k: resolve typed predecessors from v2 update body (overrides predecessorIds when present).
    List<TypedPredecessorIO> updateTypedPredecessors = null;
    if (dataObject instanceof CreateDataObjectV2IO v2ioForTyped) {
      updateTypedPredecessors = v2ioForTyped.getTypedPredecessors();
    }

    List<DataObject> newPredecessors;
    String newTypedPredecessorsJson = null;
    String[] predecessorAppIds = dataObject.getPredecessorAppIds();
    if (updateTypedPredecessors != null && !updateTypedPredecessors.isEmpty()) {
      for (TypedPredecessorIO tp : updateTypedPredecessors) {
        tp.validate();
      }
      newPredecessors = resolveTypedPredecessors(
        old.getCollection().getShepardId(),
        updateTypedPredecessors,
        dataObjectShepardId
      );
      newTypedPredecessorsJson = serialiseTypedPredecessors(updateTypedPredecessors);
    } else if (predecessorAppIds != null && predecessorAppIds.length > 0) {
      // BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH: appId-keyed predecessor list.
      // Used by v2 callers on post-L2b instances where targets have no numeric shepardId.
      // Overrides predecessorIds when non-null and non-empty.
      newPredecessors = findRelatedDataObjectsByAppId(
        old.getCollection().getShepardId(),
        predecessorAppIds,
        dataObjectShepardId
      );
      newTypedPredecessorsJson = null;
    } else {
      newPredecessors = findRelatedDataObjects(
        old.getCollection().getShepardId(),
        dataObject.getPredecessorIds(),
        dataObjectShepardId
      );
      // On update: if no typed predecessors provided, clear the stored JSON
      // (the untyped list becomes the authoritative source).
      newTypedPredecessorsJson = null;
    }

    old.setShepardId(old.getShepardId());
    old.setName(dataObject.getName());
    old.setDescription(dataObject.getDescription());
    old.setAttributes(dataObject.getAttributes());
    old.setStatus(dataObject.getStatus());
    old.setParent(newParent);
    old.setPredecessors(newPredecessors);
    // PROV1k: update stored typed predecessors JSON.
    old.setTypedPredecessorsJson(newTypedPredecessorsJson);
    // LIC1 (FAIR-1): persist license + accessRights on update. Mirrors the
    // same pattern in CollectionService — PUT is full-replace, PATCH merges
    // upstream before reaching here.
    old.setLicense(dataObject.getLicense());
    old.setAccessRights(dataObject.getAccessRights());
    // FAIR3: persist embargoEndDate on update. Nullable.
    // Note: createdByOrcid is NOT updated here — it is immutable after creation (FAIR2).
    old.setEmbargoEndDate(dataObject.getEmbargoEndDate());
    old.setUpdatedAt(dateHelper.getDate());
    old.setUpdatedBy(user);
    DataObject updated = dataObjectDAO.createOrUpdate(old);
    cutDeleted(updated);

    // P13: emit SSE change-feed event for all subscribers of this Collection.
    Collection updatedCollection = updated.getCollection();
    if (updatedCollection != null && updatedCollection.getAppId() != null) {
      collectionEventProducer.dataObjectUpdated(
        updatedCollection.getAppId(),
        updated.getAppId(),
        user.getUsername()
      );
    }

    // TPL4: refresh synthetic SemanticAnnotation nodes from updated attributes when toggle is on.
    attributeAnnotationDualWriteService.backfillFromAttributes(updated);

    return updated;
  }

  /**
   * set the deleted flag for the DataObject
   *
   * @param collectionShepardId ShepardId of the collection the dataobject is
   *                            assigned to
   * @param dataObjectShepardId identifies the DataObject to be deleted
   * @return a boolean to identify if the DataObject was successfully removed
   * @throws InvalidPathException if dataobject cannot be found or collection with
   *                              collectionShepardId does not exist
   * @throws InvalidAuthException if user does not have read or write permissions
   *                              on the collection
   */
  public void deleteDataObject(long collectionShepardId, long dataObjectShepardId) {
    DataObject toDelete = getDataObject(collectionShepardId, dataObjectShepardId);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    Date date = dateHelper.getDate();
    User user = userService.getCurrentUser();

    if (!dataObjectDAO.deleteDataObjectByShepardId(dataObjectShepardId, user, date)) {
      throw new InvalidRequestException("Could not delete DataObject with ShepardId %s".formatted(dataObjectShepardId));
    }

    // P13: emit SSE change-feed event for all subscribers of this Collection.
    Collection deletedFromCollection = toDelete.getCollection();
    if (deletedFromCollection != null && deletedFromCollection.getAppId() != null) {
      collectionEventProducer.dataObjectDeleted(
        deletedFromCollection.getAppId(),
        toDelete.getAppId(),
        user.getUsername()
      );
    }
  }

  private DataObject cutDeleted(DataObject dataObject) {
    var incoming = dataObject.getIncoming().stream().filter(i -> !i.isDeleted()).toList();
    dataObject.setIncoming(incoming);
    if (dataObject.getParent() != null && dataObject.getParent().isDeleted()) {
      dataObject.setParent(null);
    }
    var children = dataObject.getChildren().stream().filter(s -> !s.isDeleted()).toList();
    dataObject.setChildren(children);
    var predecessors = dataObject.getPredecessors().stream().filter(s -> !s.isDeleted()).toList();
    dataObject.setPredecessors(predecessors);
    var successors = dataObject.getSuccessors().stream().filter(s -> !s.isDeleted()).toList();
    dataObject.setSuccessors(successors);
    var references = dataObject.getReferences().stream().filter(ref -> !ref.isDeleted()).toList();
    dataObject.setReferences(references);
    return dataObject;
  }

  /**
   * PERF5 — batch replacement for the old per-ID loop.
   *
   * <p>Previously this method issued one {@code findByShepardId} query per element
   * in {@code referencedShepardIds}, producing N round-trips for a DataObject with N
   * predecessors. At MFFD scale (8 500+ DataObjects) this was catastrophic.
   *
   * <p>The new implementation:
   * <ol>
   *   <li>Validates self-references up front (throws {@code InvalidBodyException}).</li>
   *   <li>Calls {@link de.dlr.shepard.context.collection.daos.DataObjectDAO#findByCollectionAndShepardIds}
   *       to fetch all matching DataObjects in a single {@code WHERE d.shepardId IN $ids} query.</li>
   *   <li>Post-validates that every requested ID was found and is not deleted, and that
   *       each result belongs to the same collection — preserving the pre-PERF5 contract.</li>
   * </ol>
   */
  private List<DataObject> findRelatedDataObjects(
    long collectionShepardId,
    long[] referencedShepardIds,
    Long dataObjectShepardId
  ) {
    if (referencedShepardIds == null || referencedShepardIds.length == 0) return new ArrayList<>();

    // 1. Self-reference check (preserve existing throw semantics).
    if (dataObjectShepardId != null) {
      for (long id : referencedShepardIds) {
        if (id == dataObjectShepardId) {
          throw new InvalidBodyException("Self references are not allowed.");
        }
      }
    }

    // 2. Batch fetch — one Cypher round-trip for all IDs.
    List<Long> idList = Arrays.stream(referencedShepardIds).boxed().collect(Collectors.toList());
    List<DataObject> found = dataObjectDAO.findByCollectionAndShepardIds(collectionShepardId, idList);

    // Index by shepardId for O(1) look-up in the post-validation pass.
    var foundByShepardId = found.stream()
      .collect(Collectors.toMap(DataObject::getShepardId, d -> d));

    // 3. Post-validate every requested ID in input order (preserves error contract).
    var result = new ArrayList<DataObject>(referencedShepardIds.length);
    for (long requestedId : referencedShepardIds) {
      DataObject dataObject = foundByShepardId.get(requestedId);
      if (dataObject == null || dataObject.isDeleted()) {
        throw new InvalidBodyException(
          "The DataObject with id %d could not be found.".formatted(requestedId)
        );
      }
      // Cross-collection guard: objects that belong to a different collection
      // are simply absent from the batch result (the Cypher filters by
      // collectionShepardId), so they fall into the null branch above.
      // The explicit check below is a safety net for edge-cases where the
      // collection pointer is populated incorrectly.
      if (!dataObject.getCollection().getShepardId().equals(collectionShepardId)) {
        throw new InvalidBodyException(
          "Related data objects must belong to the same collection as the new data object"
        );
      }
      result.add(dataObject);
    }
    return result;
  }

  /**
   * BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH — resolve a String[] of appIds
   * to {@link DataObject} instances. Used when the caller supplies
   * {@code predecessorAppIds} instead of the legacy {@code predecessorIds} long[].
   */
  private List<DataObject> findRelatedDataObjectsByAppId(
    long collectionShepardId,
    String[] appIds,
    Long dataObjectShepardId
  ) {
    List<DataObject> result = new ArrayList<>(appIds.length);
    for (String appId : appIds) {
      if (appId == null || appId.isBlank()) {
        throw new InvalidBodyException("predecessorAppIds contains a null or blank entry.");
      }
      DataObject predecessor = dataObjectDAO.findByAppId(appId);
      if (predecessor == null || predecessor.isDeleted()) {
        throw new InvalidBodyException(
          "DataObject with appId '%s' could not be found.".formatted(appId)
        );
      }
      if (dataObjectShepardId != null && dataObjectShepardId.equals(predecessor.getShepardId())) {
        throw new InvalidBodyException("Self references are not allowed.");
      }
      if (!predecessor.getCollection().getShepardId().equals(collectionShepardId)) {
        throw new InvalidBodyException(
          "Related data objects must belong to the same collection as the new data object"
        );
      }
      result.add(predecessor);
    }
    return result;
  }

  private DataObject findRelatedDataObject(
    long collectionShepardId,
    Long referencedShepardId,
    Long dataObjectShepardId
  ) {
    if (referencedShepardId == null) return null;
    else if (referencedShepardId.equals(dataObjectShepardId)) throw new InvalidBodyException(
      "Self references are not allowed."
    );

    var dataObject = dataObjectDAO.findByShepardId(referencedShepardId);
    if (dataObject == null || dataObject.isDeleted()) throw new InvalidBodyException(
      "The DataObject with id %d could not be found.".formatted(referencedShepardId)
    );

    // Prevent cross collection references
    if (!dataObject.getCollection().getShepardId().equals(collectionShepardId)) throw new InvalidBodyException(
      "Related data objects must belong to the same collection as the new data object"
    );

    return dataObject;
  }

  /**
   * PROV1k — resolve a list of {@link TypedPredecessorIO} entries to
   * {@link DataObject} instances by looking up each {@code predecessorAppId}
   * via {@link DataObjectDAO#findByAppId(String)}.
   *
   * <p>Validates:
   * <ul>
   *   <li>The DataObject with the given appId must exist and not be deleted.</li>
   *   <li>It must belong to the same collection as the DataObject being created.</li>
   *   <li>It must not be the DataObject itself (self-reference guard).</li>
   * </ul>
   *
   * @param collectionShepardId the owning collection's shepardId
   * @param typedPredecessors   non-null, non-empty list of typed predecessor entries
   * @param dataObjectShepardId the DataObject being created/updated (null on create)
   * @return resolved list of predecessor DataObjects (same order as input)
   * @throws InvalidBodyException when any entry fails validation
   */
  private List<DataObject> resolveTypedPredecessors(
    long collectionShepardId,
    List<TypedPredecessorIO> typedPredecessors,
    Long dataObjectShepardId
  ) {
    List<DataObject> result = new ArrayList<>(typedPredecessors.size());
    for (TypedPredecessorIO tp : typedPredecessors) {
      DataObject predecessor = dataObjectDAO.findByAppId(tp.predecessorAppId());
      if (predecessor == null || predecessor.isDeleted()) {
        throw new InvalidBodyException(
          "TypedPredecessor: DataObject with appId '%s' could not be found."
            .formatted(tp.predecessorAppId())
        );
      }
      if (dataObjectShepardId != null && dataObjectShepardId.equals(predecessor.getShepardId())) {
        throw new InvalidBodyException("Self references are not allowed.");
      }
      if (!predecessor.getCollection().getShepardId().equals(collectionShepardId)) {
        throw new InvalidBodyException(
          "Related data objects must belong to the same collection as the new data object"
        );
      }
      result.add(predecessor);
    }
    return result;
  }

  /**
   * PROV1k — serialise a list of {@link TypedPredecessorIO} to a JSON string
   * for storage in {@code DataObject.typedPredecessorsJson}.
   *
   * <p>Best-effort: returns {@code null} on serialisation error (logged at WARN).
   *
   * @param typedPredecessors non-null list to serialise
   * @return JSON string, or {@code null} on error
   */
  private String serialiseTypedPredecessors(List<TypedPredecessorIO> typedPredecessors) {
    try {
      return MAPPER.writeValueAsString(typedPredecessors);
    } catch (JsonProcessingException e) {
      Log.warnf("PROV1k: failed to serialise typedPredecessors — stored as null. %s", e.getMessage());
      return null;
    }
  }

  /**
   * QM1b — set / update the PROV-O / FAIR²R relationship type for a single
   * predecessor edge of a DataObject. Used by the
   * {@code PATCH /v2/collections/{cid}/data-objects/{did}/predecessors/{pid}}
   * endpoint so an operator can annotate an existing predecessor with
   * {@code "fair2r:repairs"} / {@code "fair2r:concession"} / etc. without
   * tearing down and recreating the link.
   *
   * <p>Behaviour:
   * <ul>
   *   <li>The predecessor DataObject must already exist on this DataObject's
   *       predecessor list (the edge must exist before its type can be
   *       written) — otherwise throws {@link InvalidPathException} mapped to
   *       HTTP 404.</li>
   *   <li>{@code relationshipType} must be in
   *       {@link TypedPredecessorIO#ALLOWED_TYPES} — otherwise throws
   *       {@link InvalidBodyException} mapped to HTTP 400.</li>
   *   <li>Existing typed entries for other predecessors are preserved;
   *       only the entry for {@code predecessorAppId} is replaced.</li>
   * </ul>
   *
   * <p>Idempotent: re-running with the same {@code relationshipType} is a no-op.
   *
   * @param dataObjectShepardId the subject DataObject's shepardId
   * @param predecessorAppId    the predecessor's appId (must already be linked)
   * @param relationshipType    one of {@link TypedPredecessorIO#ALLOWED_TYPES}
   * @return the updated DataObject (post-save)
   */
  public DataObject setPredecessorRelationshipType(
    long dataObjectShepardId,
    String predecessorAppId,
    String relationshipType
  ) {
    if (predecessorAppId == null || predecessorAppId.isBlank()) {
      throw new InvalidBodyException("predecessorAppId must not be blank.");
    }
    if (relationshipType == null || relationshipType.isBlank()) {
      throw new InvalidBodyException("relationshipType must not be blank.");
    }
    if (!TypedPredecessorIO.ALLOWED_TYPES.contains(relationshipType)) {
      throw new InvalidBodyException(
        "QM1b: unknown relationshipType '%s'. Allowed: %s.".formatted(
          relationshipType, TypedPredecessorIO.ALLOWED_TYPES
        )
      );
    }

    DataObject subject = dataObjectDAO.findByShepardId(dataObjectShepardId);
    if (subject == null || subject.isDeleted()) {
      throw new InvalidPathException(
        "DataObject with id %d is null or deleted".formatted(dataObjectShepardId)
      );
    }

    // Auth: enforce Write on the parent Collection (mirrors update path).
    collectionService.assertIsAllowedToEditCollection(subject.getCollection().getShepardId());
    // #27-ARCHIVED — block when the Collection is frozen.
    archiveStateGuard.assertCollectionNotArchived(subject.getCollection().getShepardId());

    // Verify the predecessor edge actually exists. The PATCH endpoint annotates an
    // already-linked predecessor; creating new links goes through the create / update path.
    boolean edgeExists = subject.getPredecessors().stream()
      .filter(p -> !p.isDeleted())
      .anyMatch(p -> predecessorAppId.equals(p.getAppId()));
    if (!edgeExists) {
      throw new InvalidPathException(
        "No predecessor edge from DataObject %d to predecessor appId '%s'."
          .formatted(dataObjectShepardId, predecessorAppId)
      );
    }

    // Parse the current typedPredecessorsJson into a mutable list.
    List<TypedPredecessorIO> typed = new ArrayList<>();
    String currentJson = subject.getTypedPredecessorsJson();
    if (currentJson != null && !currentJson.isBlank()) {
      try {
        List<TypedPredecessorIO> parsed = MAPPER.readValue(
          currentJson, new TypeReference<List<TypedPredecessorIO>>() {}
        );
        if (parsed != null) typed.addAll(parsed);
      } catch (Exception e) {
        Log.warnf("QM1b: failed to parse typedPredecessorsJson on %d — rebuilding fresh. %s",
          dataObjectShepardId, e.getMessage());
        typed.clear();
      }
    }

    // Replace or insert the entry for this predecessor.
    boolean replaced = false;
    for (int i = 0; i < typed.size(); i++) {
      if (predecessorAppId.equals(typed.get(i).predecessorAppId())) {
        typed.set(i, new TypedPredecessorIO(predecessorAppId, relationshipType));
        replaced = true;
        break;
      }
    }
    if (!replaced) {
      typed.add(new TypedPredecessorIO(predecessorAppId, relationshipType));
    }

    subject.setTypedPredecessorsJson(serialiseTypedPredecessors(typed));
    return dataObjectDAO.createOrUpdate(subject);
  }

  /**
   * Only needed for fixing session problems in unit tests
   */
  public void clearSession() {
    dataObjectDAO.clearSession();
  }
}
