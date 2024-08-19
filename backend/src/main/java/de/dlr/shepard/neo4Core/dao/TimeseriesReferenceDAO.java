package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.neo4Core.entities.TimeseriesReference;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.CypherQueryHelper;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;
import java.util.List;
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
    String query =
      String.format(
        "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE ID(d)=%d ",
        CypherQueryHelper.getObjectPart("r", "TimeseriesReference", false),
        dataObjectId
      ) +
      CypherQueryHelper.getReturnPart("r");

    var queryResult = findByQuery(query, Collections.emptyMap());

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
