package de.dlr.shepard.context.semantic.services;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.version.daos.VersionableEntityConcreteDAO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * TPL4 — attributes → annotations dual-write service.
 *
 * <p>When a {@link DataObject} is saved with a non-empty {@code attributes} map
 * and the deploy-time toggle {@code shepard.tpl4.dual-write.enabled=true} is set,
 * this service creates (or refreshes) synthetic {@link SemanticAnnotation} nodes
 * whose {@code source} field is {@value Constants#ANNOTATION_SOURCE_ATTRIBUTES_BACKFILL}.
 * This makes the legacy key-value pairs visible to the ontology / SHACL layer
 * without altering the attributes map itself.
 *
 * <h2>Mapping convention</h2>
 * <ul>
 *   <li>{@code propertyIRI} = {@code "urn:shepard:attribute:<key>"} — a synthetic
 *       predicate that encodes the attribute key.  These synthetic IRIs are
 *       NOT validated against any repository and carry no
 *       {@code propertyRepository} link.</li>
 *   <li>{@code propertyName} = {@code "<key>"} — the bare key string,
 *       set so callers can display a human-readable label without parsing
 *       the IRI.</li>
 *   <li>{@code valueName} = the attribute value (string literal).  No
 *       {@code valueIRI} or {@code valueRepository} — these are uncontrolled
 *       legacy strings.</li>
 *   <li>{@code source} = {@value Constants#ANNOTATION_SOURCE_ATTRIBUTES_BACKFILL}
 *       — marks all synthetic annotations for later identification and
 *       optional cleanup once data owners migrate to first-class ontology
 *       annotations.</li>
 * </ul>
 *
 * <h2>SEMA-V6-011 — full SEMA-V6 field population</h2>
 * Since SEMA-V6-011, the new annotation shape fields are also populated:
 * <ul>
 *   <li>{@code subjectKind} = {@code "DataObject"}</li>
 *   <li>{@code subjectAppId} = the DataObject's UUID v7 (from {@link DataObject#getAppId()})</li>
 *   <li>{@code vocabularyId} = {@code null} — legacy attributes have no controlled vocabulary</li>
 *   <li>{@code sourceMode} = {@code "human"} — all legacy attributes are treated as human-authored</li>
 *   <li>{@code sourceActivityAppId}, {@code validFromMillis}, {@code validUntilMillis},
 *       {@code confidence} = {@code null}</li>
 * </ul>
 *
 * <h2>SEMA-V6-011 — import-marker key filtering</h2>
 * Keys that are transient ingest markers are NOT dual-written as annotations.
 * Filtered prefixes/values: {@code source_*}, {@code v15_*}, {@code v16_*},
 * {@code import_*}, {@code _import_*}, and exact values
 * {@code v16_pass1}, {@code v16_pass2}, {@code import_ready}.
 * A TRACE log is emitted for each skipped key.
 *
 * <h2>Idempotency</h2>
 * On update, all existing backfill-sourced annotations for the DataObject are
 * deleted and replaced with the current attributes map.  This keeps the backfill
 * in sync without duplicates when attributes are modified or removed.
 *
 * <h2>Toggle</h2>
 * The service is deploy-time-only opt-in (default {@code false}) via
 * {@code shepard.tpl4.dual-write.enabled}.  See {@code application.properties}
 * and {@code aidocs/34} for operator guidance.  Runtime flipping is intentionally
 * NOT supported — the batch backfill Cypher migration
 * ({@code V67__TPL4_attributes_to_annotations_backfill.cypher}) is the one-shot
 * migration path; the service toggle enables only the ongoing write-path
 * dual-write for new/updated DataObjects.
 *
 * @see Constants#ANNOTATION_SOURCE_ATTRIBUTES_BACKFILL
 * @see Constants#TPL4_ATTRIBUTE_PREDICATE_PREFIX
 */
@ApplicationScoped
public class AttributeAnnotationDualWriteService {

  /**
   * Predicate IRI prefix for synthetic attribute annotations.
   * Full IRI = {@code urn:shepard:attribute:<key>}.
   * Mirrors {@link Constants#TPL4_ATTRIBUTE_PREDICATE_PREFIX}.
   */
  public static final String PREDICATE_PREFIX = Constants.TPL4_ATTRIBUTE_PREDICATE_PREFIX;

  /**
   * Source tag written onto every backfill annotation node.
   * Matches {@link Constants#ANNOTATION_SOURCE_ATTRIBUTES_BACKFILL}.
   */
  public static final String BACKFILL_SOURCE = "attributes-backfill";

  @ConfigProperty(name = "shepard.tpl4.dual-write.enabled", defaultValue = "false")
  boolean dualWriteEnabled;

  @Inject
  SemanticAnnotationDAO semanticAnnotationDAO;

  @Inject
  VersionableEntityConcreteDAO versionableEntityConcreteDAO;

  /**
   * Dual-write: for each entry in {@code dataObject.attributes}, create or
   * refresh a synthetic {@link SemanticAnnotation} node tagged with
   * {@value #BACKFILL_SOURCE}.  Existing backfill annotations are removed and
   * replaced so the set stays consistent with the current attributes.
   *
   * <p>No-op when:
   * <ul>
   *   <li>the toggle is off ({@code shepard.tpl4.dual-write.enabled=false})</li>
   *   <li>{@code dataObject.attributes} is null or empty</li>
   *   <li>{@code dataObject.id} is null (pre-persist state)</li>
   * </ul>
   *
   * <p>SEMA-V6-011: import-marker keys are silently skipped (see
   * {@link #isImportMarkerKey(String)}). All other keys get the full SEMA-V6
   * annotation shape populated ({@code subjectKind}, {@code subjectAppId},
   * {@code sourceMode}).
   *
   * @param dataObject the just-persisted DataObject whose attributes should be
   *                   mirrored as semantic annotations
   */
  public void backfillFromAttributes(DataObject dataObject) {
    if (!dualWriteEnabled) {
      return;
    }
    if (dataObject == null || dataObject.getId() == null) {
      return;
    }
    Map<String, String> attrs = dataObject.getAttributes();
    if (attrs == null || attrs.isEmpty()) {
      return;
    }

    // Delete existing backfill annotations for this entity so update is idempotent.
    deleteExistingBackfillAnnotations(dataObject);

    // Reload the entity from the DAO so the OGM session reflects the deletions.
    var entity = versionableEntityConcreteDAO.findByNeo4jId(dataObject.getId());
    if (entity == null || entity.isDeleted()) {
      Log.warnf(
        "TPL4 dual-write: DataObject neo4jId=%d not found or deleted after annotation cleanup; skipping.",
        dataObject.getId()
      );
      return;
    }

    // SEMA-V6-011: capture subjectAppId once for all annotations in this batch.
    String subjectAppId = dataObject.getAppId();

    List<SemanticAnnotation> created = new ArrayList<>();
    for (Map.Entry<String, String> entry : attrs.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (key == null || value == null) continue;

      // SEMA-V6-011: skip transient ingest-marker keys (source_*, v16_*, import_*, etc.)
      if (isImportMarkerKey(key)) {
        Log.tracef("TPL4 dual-write: skipping import-marker attribute key '%s' for DataObject neo4jId=%d",
          key, dataObject.getId());
        continue;
      }

      SemanticAnnotation annotation = new SemanticAnnotation();
      annotation.setPropertyIRI(PREDICATE_PREFIX + key);
      annotation.setPropertyName(key);
      annotation.setValueName(value);
      // No valueIRI, no repositories — synthetic predicate, uncontrolled value.
      annotation.setSource(BACKFILL_SOURCE);

      // SEMA-V6-011: populate full SEMA-V6 annotation shape fields.
      annotation.setSubjectKind("DataObject");
      annotation.setSubjectAppId(subjectAppId);
      annotation.setVocabularyId(null);      // no controlled vocabulary for legacy attributes
      annotation.setSourceMode("human");     // all legacy attributes treated as human-authored
      annotation.setSourceActivityAppId(null);
      annotation.setValidFromMillis(null);
      annotation.setValidUntilMillis(null);
      annotation.setConfidence(null);

      SemanticAnnotation saved = semanticAnnotationDAO.createOrUpdate(annotation);
      entity.addAnnotation(saved);
      created.add(saved);
    }

    if (!created.isEmpty()) {
      versionableEntityConcreteDAO.createOrUpdate(entity);
      Log.debugf("TPL4 dual-write: DataObject neo4jId=%s — wrote %s backfill annotation(s) from attributes.",
        (Object) dataObject.getId().toString(), (Object) String.valueOf(created.size()));
    }
  }

  /**
   * SEMA-V6-011 — returns {@code true} for transient ingest-marker attribute keys
   * that should NOT be dual-written as semantic annotations.
   *
   * <p>Filtered patterns:
   * <ul>
   *   <li>Starts with {@code source_} (e.g. {@code source_file}, {@code source_collection})</li>
   *   <li>Starts with {@code v15_} or {@code v16_} (e.g. {@code v16_pass1}, {@code v16_pass2})</li>
   *   <li>Starts with {@code import_} or {@code _import_}
   *       (e.g. {@code import_ready}, {@code _import_batch})</li>
   *   <li>Exact match: {@code v16_pass1}, {@code v16_pass2}, {@code import_ready}</li>
   * </ul>
   *
   * @param key the attribute key to test
   * @return {@code true} if the key should be filtered out of the dual-write path
   */
  static boolean isImportMarkerKey(String key) {
    if (key == null) return false;
    return key.startsWith("source_")
      || key.startsWith("v15_")
      || key.startsWith("v16_")
      || key.startsWith("import_")
      || key.startsWith("_import_");
  }

  /**
   * Remove all existing {@code source=attributes-backfill} annotations attached
   * to the given DataObject via {@code has_annotation} edges.  Called before
   * re-creating the annotations on update so old entries don't accumulate.
   */
  private void deleteExistingBackfillAnnotations(DataObject dataObject) {
    List<SemanticAnnotation> existing = semanticAnnotationDAO.findAllSemanticAnnotationsByNeo4jId(
      dataObject.getId()
    );
    List<SemanticAnnotation> toDelete = existing
      .stream()
      .filter(a -> BACKFILL_SOURCE.equals(a.getSource()))
      .collect(Collectors.toList());

    for (SemanticAnnotation ann : toDelete) {
      if (ann.getId() != null) {
        semanticAnnotationDAO.deleteByNeo4jId(ann.getId());
      }
    }
    if (!toDelete.isEmpty()) {
      Log.debugf(
        "TPL4 dual-write: removed %s stale backfill annotation(s) from DataObject neo4jId=%s.",
        String.valueOf(toDelete.size()),
        dataObject.getId().toString()
      );
    }
  }
}
