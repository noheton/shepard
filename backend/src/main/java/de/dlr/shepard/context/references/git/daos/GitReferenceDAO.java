package de.dlr.shepard.context.references.git.daos;

import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.context.references.git.entities.GitReference;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * Lookup for {@link GitReference} rows attached to a DataObject.
 * Mirrors {@code URIReferenceDAO} — the closest analog among the
 * loose-link reference kinds.
 */
@RequestScoped
public class GitReferenceDAO extends VersionableEntityDAO<GitReference> {

  /**
   * @param dataObjectAppId Collection-side appId of the parent DataObject.
   * @return all {@code :GitReference} rows hanging off it.
   */
  public List<GitReference> findByDataObjectAppId(String dataObjectAppId) {
    String query =
      "MATCH (d:DataObject {appId: $aid})-[:has_reference]->%s ".formatted(
          CypherQueryHelper.getObjectPart("r", "GitReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");
    var queryResult = findByQuery(query, Map.of("aid", dataObjectAppId));
    return StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .toList();
  }

  /**
   * @param appId identifies the GitReference itself.
   * @return the row or {@code null} when no match.
   */
  public GitReference findByAppId(String appId) {
    String query =
      "MATCH %s WHERE r.appId = $appId ".formatted(CypherQueryHelper.getObjectPart("r", "GitReference", false)) +
      CypherQueryHelper.getReturnPart("r");
    var iter = findByQuery(query, Map.of("appId", appId));
    var it = iter.iterator();
    return it.hasNext() ? it.next() : null;
  }

  @Override
  public Class<GitReference> getEntityType() {
    return GitReference.class;
  }
}
