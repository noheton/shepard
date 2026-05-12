package de.dlr.shepard.template.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DAO for {@link ShepardTemplate}. Designed in {@code aidocs/54}.
 *
 * <p>Listings filter out {@code retired = true} rows by default —
 * the picker UI never shows superseded templates. Admin endpoints
 * can opt into seeing retired rows via {@link #list(String, boolean)}.
 */
@RequestScoped
public class ShepardTemplateDAO extends GenericDAO<ShepardTemplate> {

  /** Find a template by its {@code appId}. */
  public Optional<ShepardTemplate> findByAppId(String appId) {
    var iter = findByQuery("MATCH (t:ShepardTemplate {appId: $appId}) RETURN t LIMIT 1", Map.of("appId", appId));
    var it = iter.iterator();
    return it.hasNext() ? Optional.of(it.next()) : Optional.empty();
  }

  /**
   * List templates, optionally narrowed to one {@code templateKind}.
   * Retired rows excluded by default; pass {@code includeRetired=true}
   * for admin views.
   */
  public List<ShepardTemplate> list(String templateKind, boolean includeRetired) {
    StringBuilder cypher = new StringBuilder("MATCH (t:ShepardTemplate) WHERE 1=1");
    Map<String, Object> params = new HashMap<>();
    if (templateKind != null) {
      cypher.append(" AND t.templateKind = $kind");
      params.put("kind", templateKind);
    }
    if (!includeRetired) {
      cypher.append(" AND (t.retired IS NULL OR t.retired = false)");
    }
    cypher.append(" RETURN t ORDER BY t.name, t.version DESC");
    List<ShepardTemplate> out = new ArrayList<>();
    findByQuery(cypher.toString(), params).forEach(out::add);
    return out;
  }

  /**
   * Find the highest-version, non-retired template with the given
   * name within the given kind. Returns {@link Optional#empty()}
   * when no live row exists.
   */
  public Optional<ShepardTemplate> findLatestByName(String name, String templateKind) {
    String cypher =
      "MATCH (t:ShepardTemplate) " +
      "WHERE t.name = $name AND t.templateKind = $kind AND (t.retired IS NULL OR t.retired = false) " +
      "RETURN t ORDER BY t.version DESC LIMIT 1";
    var iter = findByQuery(cypher, Map.of("name", name, "kind", templateKind));
    var it = iter.iterator();
    return it.hasNext() ? Optional.of(it.next()) : Optional.empty();
  }

  /**
   * Mint a copy-on-write next version of the given template. Returns
   * a new {@link ShepardTemplate} (NOT saved — caller saves) with
   * {@code version} incremented and the old appId untouched.
   */
  public ShepardTemplate nextVersionOf(ShepardTemplate prior) {
    ShepardTemplate next = new ShepardTemplate();
    next.setName(prior.getName());
    next.setTemplateKind(prior.getTemplateKind());
    next.setVersion(prior.getVersion() == null ? 2 : prior.getVersion() + 1);
    next.setBody(prior.getBody());
    next.setDescription(prior.getDescription());
    next.setTags(prior.getTags() == null ? new ArrayList<>() : new ArrayList<>(prior.getTags()));
    next.setRetired(false);
    return next;
  }

  /**
   * Templates the Collection's owner has curated as visible inside
   * the Collection — the picker UI filters its list down to these
   * when the user opens the new-DataObject dialog from inside the
   * Collection. Returns empty when no curation is set.
   *
   * <p>Design: {@code aidocs/54 §3} `:ALLOWS_TEMPLATE` edge.
   */
  public List<ShepardTemplate> listAllowedForCollection(String collectionAppId) {
    String cypher =
      "MATCH (:Collection {appId: $cAppId})-[:ALLOWS_TEMPLATE]->(t:ShepardTemplate) " +
      "WHERE t.retired IS NULL OR t.retired = false " +
      "RETURN t ORDER BY t.name, t.version DESC";
    List<ShepardTemplate> out = new ArrayList<>();
    findByQuery(cypher, Map.of("cAppId", collectionAppId)).forEach(out::add);
    return out;
  }

  /**
   * Templates the Collection has cited via {@code :USES_TEMPLATE}
   * (the provenance edge — "this Collection was created from
   * template X"). Includes retired templates because past citations
   * stay valid per the copy-on-write semantics of {@code aidocs/54}.
   */
  public List<ShepardTemplate> listUsedByCollection(String collectionAppId) {
    String cypher =
      "MATCH (:Collection {appId: $cAppId})-[:USES_TEMPLATE]->(t:ShepardTemplate) " +
      "RETURN t ORDER BY t.name, t.version DESC";
    List<ShepardTemplate> out = new ArrayList<>();
    findByQuery(cypher, Map.of("cAppId", collectionAppId)).forEach(out::add);
    return out;
  }

  /**
   * Replace the {@code :ALLOWS_TEMPLATE} edge set for one Collection.
   * Existing edges are wiped; new edges are minted for every appId in
   * {@code templateAppIds}. Missing templates / missing Collection
   * are silent — the caller validates first. Idempotent.
   */
  public void setAllowedForCollection(String collectionAppId, List<String> templateAppIds) {
    session.query(
      "MATCH (:Collection {appId: $cAppId})-[r:ALLOWS_TEMPLATE]->(:ShepardTemplate) DELETE r",
      Map.of("cAppId", collectionAppId)
    );
    if (templateAppIds != null && !templateAppIds.isEmpty()) {
      session.query(
        "MATCH (c:Collection {appId: $cAppId}) " +
        "UNWIND $appIds AS tAppId " +
        "MATCH (t:ShepardTemplate {appId: tAppId}) " +
        "MERGE (c)-[:ALLOWS_TEMPLATE]->(t)",
        Map.of("cAppId", collectionAppId, "appIds", templateAppIds)
      );
    }
  }

  /**
   * Record that a Collection was instantiated from a given template —
   * idempotent {@code :USES_TEMPLATE} edge.
   */
  public void recordUsage(String collectionAppId, String templateAppId) {
    session.query(
      "MATCH (c:Collection {appId: $cAppId}), (t:ShepardTemplate {appId: $tAppId}) " +
      "MERGE (c)-[:USES_TEMPLATE]->(t)",
      Map.of("cAppId", collectionAppId, "tAppId", templateAppId)
    );
  }

  @Override
  public Class<ShepardTemplate> getEntityType() {
    return ShepardTemplate.class;
  }
}
