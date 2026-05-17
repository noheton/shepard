package de.dlr.shepard.data.file.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.data.file.entities.PayloadVersion;
import jakarta.enterprise.context.RequestScoped;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * PV1a — data-access object for {@link PayloadVersion} nodes.
 *
 * <p>Cross-references: {@code aidocs/data/46} PV1a; {@code aidocs/16} PV1a row.
 */
@RequestScoped
public class PayloadVersionDAO extends GenericDAO<PayloadVersion> {

  @Override
  public Class<PayloadVersion> getEntityType() {
    return PayloadVersion.class;
  }

  /**
   * Returns all non-deleted {@link PayloadVersion} nodes for the given
   * {@code (containerAppId, originalName)} pair, ordered by
   * {@code versionNumber} ascending (oldest version first).
   *
   * @param containerAppId the {@code appId} of the owning {@link de.dlr.shepard.data.file.entities.FileContainer}.
   * @param originalName   the file name as stored on the version nodes.
   * @return ordered list of versions; empty when none exist.
   */
  public List<PayloadVersion> findByContainerAndName(String containerAppId, String originalName) {
    String query =
      "MATCH (v:PayloadVersion) " +
      "WHERE v.containerAppId = $containerAppId " +
      "  AND v.originalName   = $originalName " +
      "  AND (v.deleted IS NULL OR v.deleted = false) " +
      "RETURN v " +
      "ORDER BY v.versionNumber ASC";
    return StreamSupport
      .stream(
        findByQuery(query, Map.of("containerAppId", containerAppId, "originalName", originalName)).spliterator(),
        false
      )
      .toList();
  }

  /**
   * Looks up a single {@link PayloadVersion} by its application-level
   * {@code appId}.
   *
   * @param appId the UUID v7 identifier of the version node.
   * @return an {@link Optional} containing the version, or empty when none
   *         exists or the node is soft-deleted.
   */
  public Optional<PayloadVersion> findByAppId(String appId) {
    String query =
      "MATCH (v:PayloadVersion) " +
      "WHERE v.appId = $appId " +
      "  AND (v.deleted IS NULL OR v.deleted = false) " +
      "RETURN v";
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? Optional.of(iter.next()) : Optional.empty();
  }

  /**
   * Returns the current maximum {@code versionNumber} for the given
   * {@code (containerAppId, originalName)} pair, or {@code 0} when no
   * version has been recorded yet.
   *
   * <p>Used by {@code FileContainerService.createFile} to compute the next
   * {@code versionNumber} without loading the full list of versions.
   *
   * @param containerAppId the {@code appId} of the owning FileContainer.
   * @param originalName   the file name.
   * @return the highest {@code versionNumber} in the graph for this pair, or
   *         {@code 0} when no matching node exists.
   */
  public long findMaxVersionNumber(String containerAppId, String originalName) {
    String query =
      "MATCH (v:PayloadVersion) " +
      "WHERE v.containerAppId = $containerAppId " +
      "  AND v.originalName   = $originalName " +
      "  AND (v.deleted IS NULL OR v.deleted = false) " +
      "RETURN coalesce(max(v.versionNumber), 0) AS maxVersion";
    org.neo4j.ogm.model.Result result = session.query(
      query,
      Map.of("containerAppId", containerAppId, "originalName", originalName)
    );
    for (Map<String, Object> row : result) {
      Object val = row.get("maxVersion");
      if (val != null) {
        return ((Number) val).longValue();
      }
    }
    return 0L;
  }
}
