package de.dlr.shepard.context.semantic.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.semantic.entities.Vocabulary;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

/**
 * SEMA-V6-002 — DAO for {@link Vocabulary} nodes.
 *
 * <p>Mirrors the {@link OntologyGitSourceDAO} pattern:
 * <ul>
 *   <li>{@link #findByAppId(String)} — primary key lookup.</li>
 *   <li>{@link #listAll()} — returns all vocabularies sorted by {@code label} ASC.</li>
 *   <li>{@link #listEnabled()} — subset of {@code listAll()} where {@code enabled = true}.</li>
 * </ul>
 *
 * <p>The DAO is {@code @RequestScoped} consistent with other OGM DAOs in this package.
 */
@RequestScoped
public class VocabularyDAO extends GenericDAO<Vocabulary> {

  /**
   * Find a vocabulary by its stable {@code appId}, or {@code null} when none exists.
   *
   * @param appId the vocabulary's UUID v7 identifier
   * @return the matching entity, or {@code null}
   */
  public Vocabulary findByAppId(String appId) {
    if (appId == null || appId.isBlank()) return null;
    Filter f = new Filter("appId", ComparisonOperator.EQUALS, appId);
    Collection<Vocabulary> hits = session.loadAll(Vocabulary.class, f, DEPTH_ENTITY);
    if (hits == null || hits.isEmpty()) return null;
    return hits.iterator().next();
  }

  /**
   * All vocabularies, ordered by {@code label} ASC (case-insensitive).
   *
   * @return list of all vocabularies; empty list when none exist
   */
  public List<Vocabulary> listAll() {
    Collection<Vocabulary> all = session.loadAll(Vocabulary.class, DEPTH_ENTITY);
    List<Vocabulary> out = new ArrayList<>(all == null ? List.of() : all);
    out.sort((a, b) -> {
      String al = a == null ? "" : (a.getLabel() == null ? "" : a.getLabel());
      String bl = b == null ? "" : (b.getLabel() == null ? "" : b.getLabel());
      return al.compareToIgnoreCase(bl);
    });
    return out;
  }

  /**
   * All enabled vocabularies — the subset exposed via autocomplete and
   * predicate lookup.
   *
   * @return enabled vocabularies ordered by {@code label} ASC
   */
  public List<Vocabulary> listEnabled() {
    List<Vocabulary> all = listAll();
    List<Vocabulary> out = new ArrayList<>(all.size());
    for (Vocabulary v : all) {
      if (v != null && v.isEnabled()) out.add(v);
    }
    return out;
  }

  // ─── SEMA-V6-014 additions ───────────────────────────────────────────────

  /**
   * SEMA-V6-014 — find a vocabulary by its canonical {@code uri}, or
   * {@code null} when none exists.
   *
   * <p>Used as a pre-check before saving a new personal vocabulary so that
   * a duplicate can return a proper 409 instead of letting the V72 unique
   * constraint propagate as a 500.
   *
   * @param uri the vocabulary's canonical IRI (e.g.
   *            {@code "urn:shepard:personal:<userAppId>:<name>"})
   * @return the matching entity, or {@code null}
   */
  public Vocabulary findByUri(String uri) {
    if (uri == null || uri.isBlank()) return null;
    Filter f = new Filter("uri", ComparisonOperator.EQUALS, uri);
    Collection<Vocabulary> hits = session.loadAll(Vocabulary.class, f, DEPTH_ENTITY);
    if (hits == null || hits.isEmpty()) return null;
    return hits.iterator().next();
  }

  /**
   * SEMA-V6-014 — list all personal vocabularies owned by a given user.
   *
   * <p>Returns only nodes whose {@code type = "PERSONAL"} AND
   * {@code ownedByUserAppId = userAppId}. The cross-user isolation
   * guarantee: two different users can each have a vocabulary with the
   * same name; this method scopes the result to the caller's appId.
   *
   * @param userAppId the caller's UUID v7 application-level identifier
   * @return personal vocabularies for the owner, ordered by {@code label} ASC;
   *         empty list when none exist
   */
  public List<Vocabulary> listPersonalByOwner(String userAppId) {
    if (userAppId == null || userAppId.isBlank()) return java.util.Collections.emptyList();
    Filter f = new Filter("ownedByUserAppId", ComparisonOperator.EQUALS, userAppId);
    Collection<Vocabulary> hits = session.loadAll(Vocabulary.class, f, DEPTH_ENTITY);
    List<Vocabulary> out = new ArrayList<>(hits == null ? List.of() : hits);
    out.sort((a, b) -> {
      String al = a == null ? "" : (a.getLabel() == null ? "" : a.getLabel());
      String bl = b == null ? "" : (b.getLabel() == null ? "" : b.getLabel());
      return al.compareToIgnoreCase(bl);
    });
    return out;
  }

  // ─── APISIMP: DB-side pagination for PersonalVocabularyRest.list() ──────────

  /** Count query for personal vocabularies owned by a given user. */
  static final String COUNT_PERSONAL_CYPHER =
    "MATCH (v:Vocabulary {ownedByUserAppId: $ownerAppId}) RETURN count(v)";

  /** Bounded list query for personal vocabularies, ordered by label ASC. */
  static final String LIST_PERSONAL_BOUNDED_CYPHER =
    "MATCH (v:Vocabulary {ownedByUserAppId: $ownerAppId}) " +
    "RETURN v " +
    "ORDER BY toLower(coalesce(v.label, '')) ASC " +
    "SKIP $skip LIMIT $limit";

  /**
   * Returns the total count of personal vocabularies owned by {@code userAppId}.
   *
   * @param userAppId the caller's UUID v7 application-level identifier
   * @return count of personal vocabularies; 0 when none exist or input is blank
   */
  public long countPersonalByOwner(String userAppId) {
    if (userAppId == null || userAppId.isBlank()) return 0L;
    var it = session.query(Long.class, COUNT_PERSONAL_CYPHER, Map.of("ownerAppId", userAppId)).iterator();
    return it.hasNext() ? it.next() : 0L;
  }

  /**
   * Returns a bounded, label-ASC page of personal vocabularies owned by {@code userAppId}.
   * DB-side SKIP/LIMIT replaces the in-memory {@code subList} previously applied in
   * {@link de.dlr.shepard.v2.vocabularies.resources.PersonalVocabularyRest#list}.
   *
   * @param userAppId the caller's UUID v7 application-level identifier
   * @param skip      rows to skip (0-based; caller ensures ≥ 0)
   * @param limit     maximum rows to return (caller ensures &gt; 0)
   * @return page of personal vocabularies, ordered by label ASC; empty list when none exist
   */
  public List<Vocabulary> listPersonalByOwner(String userAppId, int skip, int limit) {
    if (userAppId == null || userAppId.isBlank()) return java.util.Collections.emptyList();
    List<Vocabulary> out = new ArrayList<>();
    for (Vocabulary v : findByQuery(LIST_PERSONAL_BOUNDED_CYPHER,
        Map.of("ownerAppId", userAppId, "skip", skip, "limit", limit))) {
      out.add(v);
    }
    return out;
  }

  // ─── TOOLS-CONTEXT-VOCAB-BACKEND-1 + APISIMP-VOCAB-USED-BY-INMEM ────────

  /**
   * Counts the distinct vocabularies whose terms appear in at least one
   * {@code :SemanticAnnotation} on the requested entity. Used by
   * {@link de.dlr.shepard.v2.semantic.resources.VocabularyBrowseRest}
   * to populate the paged-response {@code total} without loading all nodes.
   */
  static final String COUNT_VOCAB_USED_BY_CYPHER =
    "MATCH (e {appId: $appId}) " +
    "WITH e, $scope AS scope " +
    "OPTIONAL MATCH (e)-[:HAS_DATAOBJECT*0..]->(d:DataObject) WHERE scope = 'collection' " +
    "WITH collect(DISTINCT e.appId) + collect(DISTINCT d.appId) AS subjectAppIds " +
    "MATCH (a:SemanticAnnotation) " +
    "WHERE a.subjectAppId IN subjectAppIds AND a.vocabularyId IS NOT NULL " +
    "WITH DISTINCT a.vocabularyId AS vocabId " +
    "MATCH (v:Vocabulary {appId: vocabId}) " +
    "RETURN count(v) AS c";

  /**
   * Returns a bounded, label-ASC page of vocabularies used by the entity.
   * SKIP/LIMIT are pushed to the database; no full vocabulary load occurs.
   */
  static final String LIST_VOCAB_USED_BY_PAGED_CYPHER =
    "MATCH (e {appId: $appId}) " +
    "WITH e, $scope AS scope " +
    "OPTIONAL MATCH (e)-[:HAS_DATAOBJECT*0..]->(d:DataObject) WHERE scope = 'collection' " +
    "WITH collect(DISTINCT e.appId) + collect(DISTINCT d.appId) AS subjectAppIds " +
    "MATCH (a:SemanticAnnotation) " +
    "WHERE a.subjectAppId IN subjectAppIds AND a.vocabularyId IS NOT NULL " +
    "WITH DISTINCT a.vocabularyId AS vocabId " +
    "MATCH (v:Vocabulary {appId: vocabId}) " +
    "RETURN v ORDER BY toLower(coalesce(v.label, '')) ASC " +
    "SKIP $skip LIMIT $limit";

  /**
   * APISIMP-VOCAB-USED-BY-INMEM — count of distinct vocabularies whose
   * terms are referenced by at least one {@code :SemanticAnnotation} on the
   * given entity (and, when {@code scope = "collection"}, on its descendant
   * {@code DataObject}s). DB-side count replaces the previous
   * {@link #listAll()} load + in-memory filter.
   *
   * @param entityAppId application-level identifier of the entity
   * @param scope       {@code "collection"} to walk descendants,
   *                    {@code "data-object"} for the entity only
   * @return count of distinct vocabularies in scope; 0 when none match
   */
  public long countVocabulariesUsedByEntity(String entityAppId, String scope) {
    if (entityAppId == null || entityAppId.isBlank()) return 0L;
    String normScope = "collection".equalsIgnoreCase(scope) ? "collection" : "data-object";
    var result = session.query(COUNT_VOCAB_USED_BY_CYPHER, Map.of("appId", entityAppId, "scope", normScope));
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return 0L;
    Object c = it.next().get("c");
    return c instanceof Number n ? n.longValue() : 0L;
  }

  /**
   * APISIMP-VOCAB-USED-BY-INMEM — a bounded, label-ASC page of vocabularies
   * whose terms are referenced by at least one {@code :SemanticAnnotation} on
   * the given entity. SKIP/LIMIT are pushed to the DB; no full vocabulary load
   * occurs.
   *
   * @param entityAppId application-level identifier of the entity
   * @param scope       {@code "collection"} to walk descendants,
   *                    {@code "data-object"} for the entity only
   * @param skip        rows to skip (0-based; caller ensures ≥ 0)
   * @param limit       maximum rows to return (caller ensures &gt; 0)
   * @return page of vocabularies ordered by label ASC; empty list when none match
   */
  public List<Vocabulary> listVocabulariesUsedByEntityPaged(String entityAppId, String scope, long skip, int limit) {
    if (entityAppId == null || entityAppId.isBlank()) return java.util.Collections.emptyList();
    String normScope = "collection".equalsIgnoreCase(scope) ? "collection" : "data-object";
    List<Vocabulary> out = new ArrayList<>();
    for (Vocabulary v : findByQuery(LIST_VOCAB_USED_BY_PAGED_CYPHER,
        Map.of("appId", entityAppId, "scope", normScope, "skip", skip, "limit", limit))) {
      out.add(v);
    }
    return out;
  }

  // ─── APISIMP-VOCAB-LIST-UNBOUNDED ────────────────────────────────────────

  /**
   * APISIMP-VOCAB-LIST-UNBOUNDED — total count of all {@code :Vocabulary}
   * nodes. Used by the REST layer to populate the {@code total} field of
   * the paged envelope without loading entity objects.
   */
  public long count() {
    var result = session.query("MATCH (v:Vocabulary) RETURN count(v) AS c", Map.of());
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return 0L;
    Object c = it.next().get("c");
    return c instanceof Number n ? n.longValue() : 0L;
  }

  /**
   * APISIMP-VOCAB-LIST-UNBOUNDED — page of vocabularies ordered by
   * {@code label} ASC (case-insensitive), with SKIP/LIMIT pushed to the
   * database.
   *
   * @param skip  number of rows to skip (= page × pageSize)
   * @param limit maximum rows to return (= pageSize)
   * @return ordered page; empty list when {@code skip} exceeds total
   */
  public List<Vocabulary> listPaged(long skip, int limit) {
    String query =
      "MATCH (v:Vocabulary) " +
      "RETURN v ORDER BY toLower(coalesce(v.label, '')) ASC " +
      "SKIP $skip LIMIT $limit";
    List<Vocabulary> out = new ArrayList<>();
    for (Vocabulary v : findByQuery(query, Map.of("skip", skip, "limit", limit))) {
      out.add(v);
    }
    return out;
  }

  @Override
  public Class<Vocabulary> getEntityType() {
    return Vocabulary.class;
  }
}
