package de.dlr.shepard.v2.shapes.repositories;

import de.dlr.shepard.common.neo4j.NeoConnector;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.neo4j.ogm.session.Session;

/**
 * SHAPES-APPLICABLE-FORMS — generic focus-entity resolution for
 * {@code GET /v2/shapes/applicable?focusAppId=…}.
 *
 * <p>The applicable-discovery endpoint accepts <em>any</em> entity appId
 * (DataObject, reference, container, Collection) as its focus. Per-kind DAOs
 * each only resolve their own label, so this repository runs one
 * label-agnostic Cypher probe instead — same NeoConnector-session shape as
 * {@link de.dlr.shepard.provenance.filters.EntityAppIdLookup}.
 *
 * <p>Fail-soft per the registry rules: any session error resolves to
 * {@link Optional#empty()} (the endpoint then 404s — never 500s).
 */
@ApplicationScoped
public class FocusEntityRepository {

  /**
   * The resolved focus entity.
   *
   * @param kind first Neo4j label (e.g. {@code "DataObject"}); informational
   * @param attachedTemplateAppId the {@code :ShepardTemplate} stamped on the
   *     entity at create time ({@code DataObject.attachedTemplateAppId});
   *     null for kinds without the property
   * @param collectionAppId owning Collection's appId (via
   *     {@code has_dataobject}) when the focus is a DataObject; null otherwise
   */
  public record FocusEntity(String kind, String attachedTemplateAppId, String collectionAppId) {}

  /**
   * Resolve any non-deleted entity by appId. Empty when no node carries the
   * appId (or the probe fails — fail-soft).
   */
  public Optional<FocusEntity> findByAppId(String appId) {
    if (appId == null || appId.isBlank()) {
      return Optional.empty();
    }
    try {
      Session session = NeoConnector.getInstance().getNeo4jSession();
      String query =
        "MATCH (n {appId: $appId}) WHERE n.deleted IS NULL OR n.deleted = false " +
        "OPTIONAL MATCH (c:Collection)-[:has_dataobject]->(n) " +
        "RETURN labels(n) AS labels, n.attachedTemplateAppId AS attachedTemplateAppId, " +
        "c.appId AS collectionAppId LIMIT 1";
      Map<String, Object> params = new HashMap<>();
      params.put("appId", appId);
      var result = session.query(query, params);
      var iter = result.iterator();
      if (!iter.hasNext()) {
        return Optional.empty();
      }
      Map<String, Object> row = iter.next();
      String kind = firstLabel(row.get("labels"));
      Object attached = row.get("attachedTemplateAppId");
      Object collection = row.get("collectionAppId");
      return Optional.of(
        new FocusEntity(
          kind,
          attached == null ? null : attached.toString(),
          collection == null ? null : collection.toString()
        )
      );
    } catch (RuntimeException e) {
      // Fail-soft: a probe failure must never surface as a 500 — the caller
      // treats empty as "unknown appId" (404).
      Log.warnf(e, "FocusEntityRepository: failed to resolve focus appId %s", appId);
      return Optional.empty();
    }
  }

  private static String firstLabel(Object labels) {
    if (labels instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
      return list.get(0).toString();
    }
    if (labels instanceof String[] arr && arr.length > 0) {
      return arr[0];
    }
    return null;
  }
}
