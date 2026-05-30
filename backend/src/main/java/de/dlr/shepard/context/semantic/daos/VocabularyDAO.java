package de.dlr.shepard.context.semantic.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.semantic.entities.Vocabulary;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  // ─── TOOLS-CONTEXT-VOCAB-BACKEND-1 ───────────────────────────────────────

  /**
   * Cypher used to discover which vocabulary {@code appId}s have at least
   * one {@code :SemanticAnnotation} on the requested entity (and — when
   * {@code scope = "collection"} — on every {@code DataObject} reachable
   * through the {@code [:HAS_DATAOBJECT*0..]} hierarchy).
   */
  static final String VOCAB_USED_BY_CYPHER =
    "MATCH (e {appId: $appId}) " +
    "WITH e, $scope AS scope " +
    "OPTIONAL MATCH (e)-[:HAS_DATAOBJECT*0..]->(d:DataObject) WHERE scope = 'collection' " +
    "WITH collect(DISTINCT e.appId) + collect(DISTINCT d.appId) AS subjectAppIds " +
    "MATCH (a:SemanticAnnotation) " +
    "WHERE a.subjectAppId IN subjectAppIds AND a.vocabularyId IS NOT NULL " +
    "RETURN DISTINCT a.vocabularyId AS vocabularyId";

  /**
   * TOOLS-CONTEXT-VOCAB-BACKEND-1 — return the vocabularies whose terms
   * are referenced by at least one {@code :SemanticAnnotation} attached to
   * the given entity (and, when {@code scope = "collection"}, on any of
   * its descendant {@code DataObject}s).
   *
   * @param entityAppId application-level identifier of the entity
   * @param scope       {@code "collection"} to walk descendants,
   *                    {@code "data-object"} for the entity only
   * @return vocabularies in scope; empty list when the entity has no
   *         {@code vocabularyId}-tagged annotations
   */
  public List<Vocabulary> findVocabulariesUsedByEntity(String entityAppId, String scope) {
    if (entityAppId == null || entityAppId.isBlank()) return java.util.Collections.emptyList();
    String normScope = "collection".equalsIgnoreCase(scope) ? "collection" : "data-object";
    var rows = session.query(VOCAB_USED_BY_CYPHER, Map.of("appId", entityAppId, "scope", normScope));
    Set<String> vocabIds = new HashSet<>();
    for (Map<String, Object> row : rows.queryResults()) {
      Object v = row.get("vocabularyId");
      if (v != null && !v.toString().isBlank()) vocabIds.add(v.toString());
    }
    if (vocabIds.isEmpty()) return java.util.Collections.emptyList();
    List<Vocabulary> all = listAll();
    List<Vocabulary> out = new ArrayList<>(vocabIds.size());
    for (Vocabulary v : all) {
      if (v != null && vocabIds.contains(v.getAppId())) out.add(v);
    }
    return out;
  }

  @Override
  public Class<Vocabulary> getEntityType() {
    return Vocabulary.class;
  }
}
