package de.dlr.shepard.context.references.timeseriesreference.daos;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    // C5b fix: bind dataObjectId as a Cypher parameter rather than concatenating it.
    String query =
      "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE ID(d)=$dataObjectId ".formatted(
          CypherQueryHelper.getObjectPart("r", "TimeseriesReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");

    var queryResult = findByQuery(query, Map.of("dataObjectId", dataObjectId));

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

  @Override
  public Class<TimeseriesReference> getEntityType() {
    return TimeseriesReference.class;
  }
}
