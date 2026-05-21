package de.dlr.shepard.v2.importer.io;

import java.util.List;

/**
 * IMP1 — response body for {@code GET /v2/import/context}.
 *
 * <p>Gives importers/agents a snapshot of the collection's current state so
 * they can generate manifests that are consistent with what already exists.
 * Callers should request this <em>before</em> generating a manifest so that
 * their DataObject names, semantic annotations, and predecessors align with
 * the live collection rather than a stale cached picture.
 *
 * @param collectionAppId       appId of the queried Collection
 * @param dataObjectCount       number of non-deleted DataObjects currently in
 *                              the Collection — useful as a quick size hint
 * @param collectionFingerprint SHA-256 fingerprint of {@code count|maxCreatedAt}
 *                              for the collection ({@code "sha256:hex…"}).
 *                              Matches the fingerprint stored in the
 *                              {@link de.dlr.shepard.v2.importer.entities.ImportPlan}
 *                              so callers can detect collection drift between
 *                              context-fetch and manifest submission.
 * @param semanticGraph         present only when the caller requested
 *                              {@code includeSemanticGraph=true}; {@code null}
 *                              otherwise.  When present, lists semantic
 *                              annotations already in use in this collection
 *                              so agents annotate consistently with existing
 *                              vocabulary rather than inventing duplicate terms.
 */
public record ImportContextIO(
  String collectionAppId,
  long dataObjectCount,
  String collectionFingerprint,
  SemanticGraphIO semanticGraph
) {

  /**
   * Semantic vocabulary snapshot.
   *
   * @param annotations semantic annotations currently attached to DataObjects
   *                    in this collection (may be an empty list if none exist
   *                    or if the DAO does not yet support collection-scoped
   *                    listing — see TODO in {@code ImportV2Rest}).
   */
  public record SemanticGraphIO(
    List<AnnotationSummaryIO> annotations
  ) {}

  /**
   * Abbreviated annotation descriptor for context consumers.
   *
   * <p>Carries the fields most useful to an agent deciding how to annotate a
   * new DataObject: the stable appId, the human-readable property+value names,
   * and the IRI pair for programmatic matching.
   *
   * @param appId        application-level identifier (UUID v7) of the annotation node
   * @param propertyName human-readable name of the annotated property
   * @param valueName    human-readable name of the annotation value
   * @param propertyIRI  IRI of the property (from the backing ontology)
   * @param valueIRI     IRI of the value (from the backing ontology)
   */
  public record AnnotationSummaryIO(
    String appId,
    String propertyName,
    String valueName,
    String propertyIRI,
    String valueIRI
  ) {}
}
