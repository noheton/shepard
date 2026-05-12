package de.dlr.shepard.context.references.file.daos;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * DAO for {@link FileBundleReference} (FR1a, formerly known as
 * {@code FileReferenceDAO} per {@code aidocs/53 §1.7}).
 *
 * <p>The DAO continues to query by the legacy {@code :FileReference}
 * label so the upstream-frozen REST surface and the existing
 * V11/V12 unique-constraint family keep working unchanged
 * (CLAUDE.md API-version policy). Newly persisted bundles carry both
 * labels via {@link FileBundleReference}'s {@code @Labels} field; the
 * V21 migration backfills the same shape for pre-FR1a rows.
 */
@RequestScoped
public class FileBundleReferenceDAO extends VersionableEntityDAO<FileBundleReference> {

  public List<FileBundleReference> findByDataObjectNeo4jId(long dataObjectId) {
    // L2c read-path swap: query by appId rather than the deprecated id() function.
    // Public method signature stays long for caller-compat until L2d.
    String query =
      "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d.appId=$dataObjectAppId ".formatted(
          CypherQueryHelper.getObjectPart("r", "FileReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");

    var queryResult = findByQuery(query, Map.of("dataObjectAppId", resolveAppIdOrEmpty(dataObjectId)));

    List<FileBundleReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getId().equals(dataObjectId))
      .toList();

    return result;
  }

  public List<FileBundleReference> findByDataObjectShepardId(long dataObjectShepardId) {
    String query =
      String.format(
        "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d." + Constants.SHEPARD_ID + "=%d ",
        CypherQueryHelper.getObjectPart("r", "FileReference", false),
        dataObjectShepardId
      ) +
      CypherQueryHelper.getReturnPart("r");
    var queryResult = findByQuery(query, Collections.emptyMap());

    List<FileBundleReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getShepardId().equals(dataObjectShepardId))
      .toList();

    return result;
  }

  /**
   * Look up a bundle by its appId. The query matches against the
   * legacy {@code :FileReference} label (which every bundle still
   * carries — see class-level Javadoc).
   *
   * @param appId the bundle's appId.
   * @return the row, or {@code null} when no match.
   */
  public FileBundleReference findByAppId(String appId) {
    String query =
      "MATCH %s WHERE r.appId = $appId ".formatted(CypherQueryHelper.getObjectPart("r", "FileReference", false)) +
      CypherQueryHelper.getReturnPart("r");
    var iter = findByQuery(query, Map.of("appId", appId));
    var it = iter.iterator();
    return it.hasNext() ? it.next() : null;
  }

  @Override
  public Class<FileBundleReference> getEntityType() {
    return FileBundleReference.class;
  }
}
