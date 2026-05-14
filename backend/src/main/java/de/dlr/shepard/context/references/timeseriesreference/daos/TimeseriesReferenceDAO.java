package de.dlr.shepard.context.references.timeseriesreference.daos;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RequestScoped
public class TimeseriesReferenceDAO extends VersionableEntityDAO<TimeseriesReference> {

  /**
   * Searches the database for references.
   *
   * @param dataObjectId identifies the dataObject
   * @return a List of references
   */
  public List<TimeseriesReference> findByDataObjectNeo4jId(long dataObjectId) {
    // L2c read-path swap: query by appId rather than the deprecated id() function.
    // Public method signature stays long for caller-compat until L2d.
    String query =
      "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d.appId=$dataObjectAppId ".formatted(
          CypherQueryHelper.getObjectPart("r", "TimeseriesReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");

    var queryResult = findByQuery(query, Map.of("dataObjectAppId", resolveAppIdOrEmpty(dataObjectId)));

    List<TimeseriesReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getId().equals(dataObjectId))
      .toList();

    return result;
  }

  public List<TimeseriesReference> findByDataObjectShepardId(long dataObjectShepardId) {
    String query =
      String.format(
        "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d." + Constants.SHEPARD_ID + "=%d ",
        CypherQueryHelper.getObjectPart("r", "TimeseriesReference", false),
        dataObjectShepardId
      ) +
      CypherQueryHelper.getReturnPart("r");

    var queryResult = findByQuery(query, Collections.emptyMap());

    List<TimeseriesReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getShepardId().equals(dataObjectShepardId))
      .toList();

    return result;
  }

  /**
   * AI1c — list non-deleted {@code TimeseriesReference} nodes whose
   * {@code qualityScore} is either unset or whose {@code lastScoredAt}
   * is older than the supplied cutoff (epoch millis). Capped at
   * {@code limit} rows so the rescoring job has bounded I/O per tick.
   *
   * <p>The query intentionally matches only references with a non-null
   * {@code timeseriesContainer} — a stranded reference (container
   * deleted) can't be scored, no point listing it.
   *
   * @param staleCutoffMillis epoch-millis cutoff; refs scored at or
   *     before this point are considered stale
   * @param limit            hard cap on rows returned (batch size)
   * @return up to {@code limit} references in need of scoring
   */
  public List<TimeseriesReference> findNeedingScoring(long staleCutoffMillis, int limit) {
    String query =
      "MATCH (r:TimeseriesReference) " +
      "WHERE r.deleted = FALSE " +
      "AND EXISTS { MATCH (r)-[:is_in_container]->(:TimeseriesContainer) } " +
      "AND (r.qualityScore IS NULL OR r.lastScoredAt IS NULL OR r.lastScoredAt < $staleCutoff) " +
      "RETURN r LIMIT $limit";

    var queryResult = findByQuery(
      query,
      Map.of("staleCutoff", staleCutoffMillis, "limit", limit)
    );

    return StreamSupport.stream(queryResult.spliterator(), false).collect(Collectors.toList());
  }

  public TimeseriesReference findByAppId(String appId) {
    String query =
      "MATCH " +
      CypherQueryHelper.getObjectPart("r", "TimeseriesReference", false) +
      " WHERE r.appId = $appId " +
      CypherQueryHelper.getReturnPart("r");
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  @Override
  public Class<TimeseriesReference> getEntityType() {
    return TimeseriesReference.class;
  }
}
