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

    List<SemanticAnnotation> created = new ArrayList<>();
    for (Map.Entry<String, String> entry : attrs.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (key == null || value == null) continue;

      SemanticAnnotation annotation = new SemanticAnnotation();
      annotation.setPropertyIRI(PREDICATE_PREFIX + key);
      annotation.setPropertyName(key);
      annotation.setValueName(value);
      // No valueIRI, no repositories — synthetic predicate, uncontrolled value.
      annotation.setSource(BACKFILL_SOURCE);

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
