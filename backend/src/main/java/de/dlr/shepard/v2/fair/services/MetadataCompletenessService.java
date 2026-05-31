package de.dlr.shepard.v2.fair.services;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.v2.fair.io.CheckResultIO;
import de.dlr.shepard.v2.fair.io.MetadataCompletenessScoreIO;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * FAIR4 — Pure scoring service for
 * {@code GET /v2/collections/{appId}/metadata-completeness}.
 *
 * <p>Computes the same 9-check, 0–100 score as the frontend
 * {@code computeMetadataCompleteness()} helper in
 * {@code frontend/utils/metadataCompleteness.ts}. The check IDs,
 * labels, weights, and thresholds are kept in sync with the TS
 * implementation so UI + API results are directly comparable.
 *
 * <h3>Checks and weights (must match the TS helper)</h3>
 * <table>
 *   <tr><th>checkId</th><th>weight</th><th>condition</th></tr>
 *   <tr><td>name</td><td>10</td><td>collection.name non-blank</td></tr>
 *   <tr><td>description</td><td>15</td><td>description ≥ 50 chars</td></tr>
 *   <tr><td>license</td><td>20</td><td>SPDX string non-blank</td></tr>
 *   <tr><td>accessRights</td><td>10</td><td>non-blank</td></tr>
 *   <tr><td>creatorOrcid</td><td>10</td><td>creator user has ORCID set</td></tr>
 *   <tr><td>semanticAnnotation</td><td>10</td><td>≥ 1 annotation on any DataObject</td></tr>
 *   <tr><td>labJournal</td><td>5</td><td>≥ 1 lab-journal entry</td></tr>
 *   <tr><td>keywords</td><td>5</td><td>≥ 1 keyword-predicate annotation</td></tr>
 *   <tr><td>dataObjects</td><td>15</td><td>≥ 1 DataObject</td></tr>
 * </table>
 *
 * <p>Total: 100 points. Score band thresholds (mirror client):
 * {@code < 50} = not publication-ready; {@code 50–79} = missing key
 * FAIR fields; {@code ≥ 80} = DMP-grade.
 *
 * <h3>Semantic-annotation count (server side)</h3>
 * The server counts annotations on the collection <em>directly</em>
 * (via {@code (:Collection)-[:has_annotation]->(:SemanticAnnotation)}
 * AND via
 * {@code (:Collection)-[:has_dataobject]->(:DataObject)-[:has_annotation]->(:SemanticAnnotation)})
 * to mirror what the client widget fetches via
 * {@code AnnotatedCollection.fetchAnnotations()}.
 *
 * <h3>Keyword count</h3>
 * Annotations whose {@code propertyIRI} contains one of the
 * well-known keyword predicate fragments (schema:keywords,
 * dcat:keyword, dc:subject) are counted as keywords —
 * matching the FAIR8 follow-up intent noted in the TS helper.
 *
 * <h3>ORCID</h3>
 * Uses {@code collection.createdByOrcid} (the field stamped on
 * the {@code :Collection} node itself at creation time from the
 * creating user's profile).
 *
 * <p>No side effects — pure read projection. Safe to call on every
 * request.
 *
 * <p>Cross-references: {@code aidocs/16} FAIR4 row;
 * {@code frontend/utils/metadataCompleteness.ts} (canonical source
 * of check weights and IDs).
 */
@ApplicationScoped
public class MetadataCompletenessService {

  /** Minimum description length to count as "rich" (mirrors TS DESCRIPTION_MIN_CHARS). */
  private static final int DESCRIPTION_MIN_CHARS = 50;

  /**
   * Keyword predicate IRI fragments. An annotation is a "keyword" annotation when
   * its {@code propertyIRI} contains any of these substrings — mirrors the FAIR8
   * follow-up intent in the TS helper ({@code keywordCount} consumer).
   */
  private static final List<String> KEYWORD_PREDICATES = List.of(
    "schema.org/keywords",
    "schema.org/keyword",
    "dcat#keyword",
    "dc/terms/subject",
    "dc/subject",
    "dcat:keyword",
    "schema:keywords"
  );

  /**
   * Compute the metadata completeness score for the given Collection.
   *
   * <p>The Collection must be loaded with its {@code dataObjects} relationship
   * populated (the standard
   * {@code CollectionService.getCollectionWithDataObjectsAndIncomingReferences}
   * load depth is sufficient). Secondary counts (annotation counts,
   * lab-journal count) are resolved via lightweight Cypher queries against the
   * live Neo4j session.
   *
   * @param collection the fully-loaded Collection
   * @return a {@link MetadataCompletenessScoreIO} with score, maxScore,
   *         percentage, and per-check breakdown
   */
  public MetadataCompletenessScoreIO compute(Collection collection) {
    String collAppId = collection.getAppId() != null ? collection.getAppId() : "";

    // ── Lazy count queries (only executed if we reach those checks) ──────────
    Supplier<Long> annotationCount = memoize(() -> countAnnotations(collAppId));
    Supplier<Long> labJournalCount = memoize(() -> countLabJournalEntries(collAppId));
    Supplier<Long> keywordCount = memoize(() -> countKeywordAnnotations(collAppId));

    // ── Build the check list (order matches the TS helper) ───────────────────
    List<CheckResultIO> checks = new ArrayList<>();

    // name — 10 pts
    boolean namePassed = collection.getName() != null &&
      !collection.getName().trim().isEmpty();
    checks.add(new CheckResultIO(
      "name",
      "Collection has a name",
      namePassed,
      10,
      "DataCite §3 (Title) + F-UJI FsF-F2-01M — required for every published dataset."
    ));

    // description — 15 pts
    String desc = collection.getDescription() != null ? collection.getDescription() : "";
    boolean descPassed = desc.trim().length() >= DESCRIPTION_MIN_CHARS;
    checks.add(new CheckResultIO(
      "description",
      "Description ≥ " + DESCRIPTION_MIN_CHARS + " characters",
      descPassed,
      15,
      "DataCite §17 (Description) + F-UJI FsF-F2-01M / FsF-R1-01MD — " +
        "at least " + DESCRIPTION_MIN_CHARS + " characters distinguishes a stub from a real abstract."
    ));

    // license — 20 pts
    String license = collection.getLicense();
    boolean licensePassed = license != null && !license.trim().isEmpty();
    checks.add(new CheckResultIO(
      "license",
      "License (SPDX) set",
      licensePassed,
      20,
      "DataCite §16 (Rights) + F-UJI FsF-R1.1-01M — the single biggest blocker to publication. " +
        "Without a license the dataset is legally unusable."
    ));

    // accessRights — 10 pts
    String ar = collection.getAccessRights();
    boolean arPassed = ar != null && !ar.trim().isEmpty();
    checks.add(new CheckResultIO(
      "accessRights",
      "Access rights set",
      arPassed,
      10,
      "DataCite §16 (Rights) + F-UJI FsF-A1-01M — declares open / restricted / closed / embargoed. " +
        "Required for Horizon Europe embargoed deposits."
    ));

    // creatorOrcid — 10 pts
    // Uses the ORCID stamped on the Collection node itself at creation time.
    String orcid = collection.getCreatedByOrcid();
    boolean orcidPassed = orcid != null && !orcid.trim().isEmpty();
    checks.add(new CheckResultIO(
      "creatorOrcid",
      "Creator has ORCID",
      orcidPassed,
      10,
      "DataCite §2 (Creator) + F-UJI FsF-F2-01M — researcher PID. " +
        "Without ORCID the citation falls back to a bare username with no resolver."
    ));

    // semanticAnnotation — 10 pts
    boolean semanticPassed = annotationCount.get() > 0;
    checks.add(new CheckResultIO(
      "semanticAnnotation",
      "At least one semantic annotation",
      semanticPassed,
      10,
      "F-UJI FsF-I2-01M — controlled-vocabulary annotation makes the Collection findable " +
        "via ontology-aware catalogues (FAIR I1 + I2)."
    ));

    // labJournal — 5 pts
    boolean ljPassed = labJournalCount.get() > 0;
    checks.add(new CheckResultIO(
      "labJournal",
      "At least one lab journal entry",
      ljPassed,
      5,
      "F-UJI FsF-R1.2-01M — narrative context that the machine-readable provenance graph alone " +
        "cannot provide (FAIR R1.2)."
    ));

    // keywords — 5 pts
    boolean kwPassed = keywordCount.get() > 0;
    checks.add(new CheckResultIO(
      "keywords",
      "At least one keyword annotation",
      kwPassed,
      5,
      "F-UJI FsF-F2-01M (Keywords sub-field) — subject tags make the Collection discoverable " +
        "in catalogue keyword searches (DataCite §6 Subject)."
    ));

    // dataObjects — 15 pts
    int doCount = collection.getDataObjects() != null ? collection.getDataObjects().size() : 0;
    boolean doPassed = doCount > 0;
    checks.add(new CheckResultIO(
      "dataObjects",
      "Has at least one DataObject",
      doPassed,
      15,
      "F-UJI FsF-F3-01M — a Collection with zero DataObjects has nothing for harvesters to enumerate " +
        "(FAIR F2 / F3)."
    ));

    // ── Score computation ─────────────────────────────────────────────────────
    int maxScore = checks.stream().mapToInt(CheckResultIO::getWeight).sum();
    int earned = checks.stream()
      .filter(CheckResultIO::isPassed)
      .mapToInt(CheckResultIO::getWeight)
      .sum();
    int score = Math.max(0, Math.min(earned, 100));
    double percentage = maxScore > 0
      ? Math.round(100.0 * score / maxScore * 100.0) / 100.0
      : 0.0;

    return new MetadataCompletenessScoreIO(collAppId, score, maxScore, percentage, checks);
  }

  // ── Count queries ───────────────────────────────────────────────────────────

  /**
   * Count semantic annotations that are either directly on the Collection or
   * on any of its non-deleted DataObjects. Mirrors the client widget's
   * {@code AnnotatedCollection.fetchAnnotations()} which fetches annotations
   * on the Collection entity itself.
   */
  private long countAnnotations(String collectionAppId) {
    if (collectionAppId == null || collectionAppId.isBlank()) return 0L;
    // Count annotations directly on the Collection node + on its DataObjects.
    String query =
      "MATCH (c:Collection {appId: $cAppId}) " +
      "OPTIONAL MATCH (c)-[:has_annotation]->(a1:SemanticAnnotation) " +
      "OPTIONAL MATCH (c)-[:has_dataobject]->(d:DataObject)-[:has_annotation]->(a2:SemanticAnnotation) " +
      "WHERE (d.deleted IS NULL OR d.deleted = false) " +
      "RETURN count(DISTINCT a1) + count(DISTINCT a2) AS total";
    return runCountQuery(query, Map.of("cAppId", collectionAppId));
  }

  /**
   * Count non-deleted lab-journal entries across all non-deleted DataObjects
   * in the Collection. Mirrors the client widget's
   * {@code useFetchCollectionLabJournalEntries} result length.
   */
  private long countLabJournalEntries(String collectionAppId) {
    if (collectionAppId == null || collectionAppId.isBlank()) return 0L;
    String query =
      "MATCH (c:Collection {appId: $cAppId})" +
      "-[:has_dataobject]->(d:DataObject)" +
      "-[:has_labJournalEntry]->(lje:LabJournalEntry) " +
      "WHERE (d.deleted IS NULL OR d.deleted = false) " +
      "  AND (lje.deleted IS NULL OR lje.deleted = false) " +
      "RETURN count(DISTINCT lje) AS total";
    return runCountQuery(query, Map.of("cAppId", collectionAppId));
  }

  /**
   * Count annotations on the Collection whose {@code propertyIRI} matches
   * any of the well-known keyword predicate fragments (schema:keywords,
   * dcat:keyword, dc:subject). Mirrors the FAIR8 keyword-count intent in
   * the TS helper ({@code keywordCount} input field).
   */
  private long countKeywordAnnotations(String collectionAppId) {
    if (collectionAppId == null || collectionAppId.isBlank()) return 0L;
    // Build a WHERE clause that matches any keyword predicate fragment.
    StringBuilder where = new StringBuilder(
      "MATCH (c:Collection {appId: $cAppId}) " +
      "OPTIONAL MATCH (c)-[:has_annotation]->(a1:SemanticAnnotation) " +
      "OPTIONAL MATCH (c)-[:has_dataobject]->(d:DataObject)-[:has_annotation]->(a2:SemanticAnnotation) " +
      "WHERE (d.deleted IS NULL OR d.deleted = false) " +
      "WITH [a1, a2] AS pairs UNWIND pairs AS a " +
      "WHERE a IS NOT NULL AND ("
    );
    for (int i = 0; i < KEYWORD_PREDICATES.size(); i++) {
      if (i > 0) where.append(" OR ");
      where.append("toLower(a.propertyIRI) CONTAINS '").append(KEYWORD_PREDICATES.get(i).toLowerCase()).append("'");
    }
    where.append(") RETURN count(DISTINCT a) AS total");
    return runCountQuery(where.toString(), Map.of("cAppId", collectionAppId));
  }

  private long runCountQuery(String query, Map<String, Object> params) {
    try {
      var session = NeoConnector.getInstance().getNeo4jSession();
      var result = session.query(query, params);
      for (var row : result.queryResults()) {
        Object cnt = row.get("total");
        if (cnt instanceof Number n) return n.longValue();
      }
      return 0L;
    } catch (Exception e) {
      // Secondary read — fail-soft, return 0 to bias the score conservative.
      return 0L;
    }
  }

  // ── Memoization helper ──────────────────────────────────────────────────────

  /**
   * Lazily evaluates a supplier exactly once and caches the result.
   * Avoids running the Cypher query if the check is unreachable (future
   * short-circuit optimisation).
   */
  private static <T> Supplier<T> memoize(Supplier<T> supplier) {
    final Object[] result = new Object[1];
    final boolean[] computed = { false };
    return () -> {
      if (!computed[0]) {
        result[0] = supplier.get();
        computed[0] = true;
      }
      @SuppressWarnings("unchecked")
      T t = (T) result[0];
      return t;
    };
  }
}
