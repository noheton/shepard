package de.dlr.shepard.plugins.references.dbpediadatabus.daos;

import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import de.dlr.shepard.plugins.references.dbpediadatabus.entities.DbpediaDatabusReference;
import jakarta.enterprise.context.RequestScoped;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * Lookup for {@link DbpediaDatabusReference} rows attached to a
 * DataObject. Mirrors {@code GitReferenceDAO} (G1a) — both inherit
 * from {@code BasicReference} and expose
 * {@code findByDataObjectAppId} + {@code findByAppId}.
 */
@RequestScoped
public class DbpediaDatabusReferenceDAO extends VersionableEntityDAO<DbpediaDatabusReference> {

  public List<DbpediaDatabusReference> findByDataObjectAppId(String dataObjectAppId) {
    String query =
      "MATCH (d:DataObject {appId: $aid})-[:has_reference]->%s ".formatted(
          CypherQueryHelper.getObjectPart("r", "DbpediaDatabusReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");
    var queryResult = findByQuery(query, Map.of("aid", dataObjectAppId));
    return StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .toList();
  }

  public DbpediaDatabusReference findByAppId(String appId) {
    String query =
      "MATCH %s WHERE r.appId = $appId ".formatted(
          CypherQueryHelper.getObjectPart("r", "DbpediaDatabusReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");
    var iter = findByQuery(query, Map.of("appId", appId));
    var it = iter.iterator();
    return it.hasNext() ? it.next() : null;
  }

  /** All references across all DataObjects — used by the refresh-all admin endpoint. */
  public List<DbpediaDatabusReference> findAllReferences() {
    String query =
      "MATCH %s ".formatted(CypherQueryHelper.getObjectPart("r", "DbpediaDatabusReference", false)) +
      CypherQueryHelper.getReturnPart("r");
    var queryResult = findByQuery(query, Map.of());
    return StreamSupport.stream(queryResult.spliterator(), false).toList();
  }

  @Override
  public Class<DbpediaDatabusReference> getEntityType() {
    return DbpediaDatabusReference.class;
  }
}
