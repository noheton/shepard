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

  @Override
  public Class<ShepardTemplate> getEntityType() {
    return ShepardTemplate.class;
  }
}
