package de.dlr.shepard.v2.quality.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.v2.quality.daos.DataQualityRequirementDAO;
import de.dlr.shepard.v2.quality.entities.DataQualityRequirement;
import de.dlr.shepard.v2.quality.io.CreateDQRIO;
import de.dlr.shepard.v2.quality.io.DQRIO;
import de.dlr.shepard.v2.quality.io.DQRResultIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * TPL10 — business logic for Data Quality Requirements.
 *
 * <p>Auth rules (consistent with other v2 Collection-scoped surfaces):
 * <ul>
 *   <li>GET list        — requires Read on the Collection.</li>
 *   <li>POST assign     — requires Write on the Collection.</li>
 *   <li>DELETE remove   — requires Write on the Collection.</li>
 *   <li>POST evaluate   — requires Read on the Collection.</li>
 * </ul>
 */
@RequestScoped
public class DataQualityRequirementService {

  @Inject
  DataQualityRequirementDAO dao;

  @Inject
  CollectionPropertiesDAO collectionPropertiesDAO;

  @Inject
  PermissionsService permissionsService;

  // ─── Public API ───────────────────────────────────────────────────────────

  /**
   * List all DQRs assigned to a Collection.
   *
   * @param collectionAppId the Collection's appId
   * @param caller          authenticated username
   * @return list of DQRIO; empty when none are assigned
   */
  public List<DQRIO> list(String collectionAppId, String caller) {
    assertCollectionReadable(collectionAppId, caller);
    return dao.findByCollectionAppId(collectionAppId)
      .stream()
      .map(DQRIO::from)
      .toList();
  }

  /**
   * Create a new DQR and assign it to a Collection.
   *
   * @param collectionAppId the Collection's appId
   * @param body            creation payload
   * @param caller          authenticated username
   * @return the persisted DQR as a DQRIO
   */
  public DQRIO assign(String collectionAppId, CreateDQRIO body, String caller) {
    assertCollectionWritable(collectionAppId, caller);

    DataQualityRequirement dqr = new DataQualityRequirement();
    dqr.setName(body.name());
    dqr.setDescription(body.description());
    dqr.setRuleType(body.ruleType());
    dqr.setRuleParam(body.ruleParam());
    dqr.setSeverity(body.severity() != null ? body.severity() : "ERROR");
    dqr.setEnabled(body.enabled() == null || body.enabled());

    DataQualityRequirement saved = dao.createOrUpdate(dqr);
    dao.assignToCollection(saved.getAppId(), collectionAppId);
    Log.infof("TPL10: created DQR %s (%s) for collection %s", saved.getAppId(), saved.getRuleType(), collectionAppId);
    return DQRIO.from(saved);
  }

  /**
   * Remove a DQR from a Collection (and delete the node entirely).
   *
   * @param collectionAppId the Collection's appId
   * @param dqrAppId        the DQR's appId
   * @param caller          authenticated username
   * @throws NotFoundException when the DQR is not found or not assigned to this Collection
   */
  public void remove(String collectionAppId, String dqrAppId, String caller) {
    assertCollectionWritable(collectionAppId, caller);

    DataQualityRequirement dqr = dao.findByAppId(dqrAppId);
    if (dqr == null) {
      throw new NotFoundException("No DataQualityRequirement with appId " + dqrAppId);
    }
    if (!dao.isAssignedToCollection(dqrAppId, collectionAppId)) {
      throw new NotFoundException(
        "DataQualityRequirement " + dqrAppId + " is not assigned to collection " + collectionAppId
      );
    }
    dao.deleteWithRelationships(dqr.getId());
    Log.infof("TPL10: deleted DQR %s from collection %s", dqrAppId, collectionAppId);
  }

  /**
   * Run all enabled DQRs for a Collection and return per-DataObject results.
   *
   * <p>Implemented rule types:
   * <ul>
   *   <li>{@code ANNOTATION_REQUIRED} — checks that each DataObject has a
   *       non-null attribute value for the key given in {@code ruleParam}.
   *       The attribute is stored as the Neo4j property {@code attributes||<key>}.</li>
   * </ul>
   *
   * <p>Stub rule types (always return PASS with a TODO message):
   * {@code NO_TIMESERIES_GAP}, {@code FILE_COUNT_MIN}, {@code CUSTOM_CYPHER}.
   *
   * @param collectionAppId the Collection's appId
   * @param caller          authenticated username
   * @return list of DQRResultIO; one entry per (DQR, DataObject) pair for the
   *         evaluated DQRs; empty when no DQRs are enabled
   */
  public List<DQRResultIO> evaluate(String collectionAppId, String caller) {
    assertCollectionReadable(collectionAppId, caller);

    List<DataQualityRequirement> dqrs = dao.findByCollectionAppId(collectionAppId)
      .stream()
      .filter(DataQualityRequirement::isEnabled)
      .toList();

    if (dqrs.isEmpty()) {
      return List.of();
    }

    // Fetch DataObject appIds once for stub evaluators; ANNOTATION_REQUIRED uses its own targeted query.
    List<String> allDataObjectAppIds = dao.findDataObjectAppIds(collectionAppId);

    List<DQRResultIO> results = new ArrayList<>();

    for (DataQualityRequirement dqr : dqrs) {
      DataQualityRequirement.RuleType type;
      try {
        type = DataQualityRequirement.RuleType.valueOf(dqr.getRuleType());
      } catch (IllegalArgumentException e) {
        Log.warnf("TPL10: unknown ruleType '%s' on DQR %s — skipping", dqr.getRuleType(), dqr.getAppId());
        continue;
      }

      switch (type) {
        case ANNOTATION_REQUIRED -> results.addAll(evaluateAnnotationRequired(dqr, collectionAppId, allDataObjectAppIds));
        case NO_TIMESERIES_GAP   -> results.addAll(evaluateStub(dqr, allDataObjectAppIds, "NO_TIMESERIES_GAP evaluation not yet implemented"));
        case FILE_COUNT_MIN      -> results.addAll(evaluateStub(dqr, allDataObjectAppIds, "FILE_COUNT_MIN evaluation not yet implemented"));
        case CUSTOM_CYPHER       -> results.addAll(evaluateStub(dqr, allDataObjectAppIds, "CUSTOM_CYPHER evaluation not yet implemented"));
      }
    }

    return results;
  }

  // ─── Evaluators ──────────────────────────────────────────────────────────

  /**
   * ANNOTATION_REQUIRED: passes when the DataObject has a non-null, non-blank
   * attribute value for the key named by {@code dqr.getRuleParam()}.
   *
   * <p>Attributes are stored as Neo4j node properties using the OGM
   * {@code @Properties(delimiter = "||")} convention: the Neo4j property
   * key is {@code attributes||<key>}. The DAO uses a dedicated Cypher query
   * that backtick-quotes the dynamic property name and returns only the appIds
   * of DataObjects that pass, so we can compare against the full set.
   *
   * @param dqr              the DQR being evaluated
   * @param collectionAppId  appId of the Collection being scanned
   * @param allDoAppIds      pre-fetched list of all non-deleted DataObject appIds in the Collection
   */
  private List<DQRResultIO> evaluateAnnotationRequired(
    DataQualityRequirement dqr,
    String collectionAppId,
    List<String> allDoAppIds
  ) {
    String requiredKey = dqr.getRuleParam();
    Set<String> passing = dao.findDataObjectsHavingAttribute(collectionAppId, requiredKey);

    List<DQRResultIO> results = new ArrayList<>();
    for (String doAppId : allDoAppIds) {
      if (passing.contains(doAppId)) {
        results.add(DQRResultIO.pass(dqr.getAppId(), doAppId));
      } else {
        results.add(DQRResultIO.fail(
          dqr.getAppId(),
          doAppId,
          "Missing annotation: " + requiredKey
        ));
      }
    }
    return results;
  }

  /**
   * Stub evaluator for unimplemented rule types — returns PASS for every
   * DataObject. Logs a debug message with the TODO explanation.
   */
  private List<DQRResultIO> evaluateStub(
    DataQualityRequirement dqr,
    List<String> allDoAppIds,
    String todoMessage
  ) {
    List<DQRResultIO> results = new ArrayList<>();
    for (String doAppId : allDoAppIds) {
      results.add(DQRResultIO.pass(dqr.getAppId(), doAppId));
    }
    Log.debugf("TPL10: stub evaluation for DQR %s (%s): %s", dqr.getAppId(), dqr.getRuleType(), todoMessage);
    return results;
  }

  // ─── Auth helpers ─────────────────────────────────────────────────────────

  private long resolveCollectionOgmId(String collectionAppId) {
    Optional<Long> ogmId = collectionPropertiesDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) {
      throw new NotFoundException("No Collection with appId " + collectionAppId);
    }
    return ogmId.get();
  }

  private void assertCollectionReadable(String collectionAppId, String caller) {
    long ogmId = resolveCollectionOgmId(collectionAppId);
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Read, caller, 0L)) {
      throw new ForbiddenException("Caller lacks Read on collection " + collectionAppId);
    }
  }

  private void assertCollectionWritable(String collectionAppId, String caller) {
    long ogmId = resolveCollectionOgmId(collectionAppId);
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Write, caller, 0L)) {
      throw new ForbiddenException("Caller lacks Write on collection " + collectionAppId);
    }
  }
}
