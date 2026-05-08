package de.dlr.shepard.v2.admin.services;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.v2.admin.io.PermissionAuditEntryIO;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service for {@code GET /v2/admin/permission-audit} — lists every
 * {@code BasicEntity} that lacks a {@code :has_permissions} edge
 * (the C3 fail-closed default would deny all access to those, so
 * this is the operational surface to find them).
 *
 * <p>Reads via direct Cypher (not OGM) because the result-shape is a
 * lightweight summary, not the full entity graph. Returns at most
 * {@link #DEFAULT_LIMIT} rows; operators with thousands of orphans
 * should fix the deployment, not read the whole list.
 */
@RequestScoped
public class PermissionAuditService {

  static final int DEFAULT_LIMIT = 1000;

  public List<PermissionAuditEntryIO> listOrphans() {
    return listOrphans(DEFAULT_LIMIT);
  }

  public List<PermissionAuditEntryIO> listOrphans(int limit) {
    var session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return Collections.emptyList();
    String cypher =
      "MATCH (e:BasicEntity) " +
      "WHERE NOT (e)-[:has_permissions]->(:Permissions) " +
      "RETURN id(e) AS id, e.appId AS appId, e.name AS name, labels(e) AS labels " +
      "ORDER BY id(e) " +
      "LIMIT $limit";
    var result = session.query(cypher, Map.of("limit", limit));
    List<PermissionAuditEntryIO> out = new ArrayList<>();
    for (Map<String, Object> row : result.queryResults()) {
      Object idObj = row.get("id");
      long id = idObj instanceof Number n ? n.longValue() : -1L;
      String appId = Objects.toString(row.get("appId"), null);
      String name = Objects.toString(row.get("name"), null);
      List<String> labels = new ArrayList<>();
      Object labelsObj = row.get("labels");
      if (labelsObj instanceof Iterable<?> it) {
        for (Object item : it) {
          if (item != null) labels.add(item.toString());
        }
      } else if (labelsObj instanceof Object[] arr) {
        for (Object item : arr) {
          if (item != null) labels.add(item.toString());
        }
      }
      out.add(new PermissionAuditEntryIO(id, appId, labels, name));
    }
    return out;
  }
}
