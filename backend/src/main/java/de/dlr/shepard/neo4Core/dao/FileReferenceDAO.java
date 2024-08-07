package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.neo4Core.entities.FileReference;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.CypherQueryHelper;
import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

public class FileReferenceDAO extends VersionableEntityDAO<FileReference> {

  public List<FileReference> findByDataObjectNeo4jId(long dataObjectId) {
    String query =
      String.format(
        "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE ID(d)=%d ",
        CypherQueryHelper.getObjectPart("r", "FileReference", false),
        dataObjectId
      ) +
      CypherQueryHelper.getReturnPart("r");

    var queryResult = findByQuery(query, Collections.emptyMap());

    List<FileReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getId().equals(dataObjectId))
      .toList();

    return result;
  }

  public List<FileReference> findByDataObjectShepardId(long dataObjectShepardId) {
    String query =
      String.format(
        "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d." + Constants.SHEPARD_ID + "=%d ",
        CypherQueryHelper.getObjectPart("r", "FileReference", false),
        dataObjectShepardId
      ) +
      CypherQueryHelper.getReturnPart("r");
    var queryResult = findByQuery(query, Collections.emptyMap());

    List<FileReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getShepardId().equals(dataObjectShepardId))
      .toList();

    return result;
  }

  @Override
  public Class<FileReference> getEntityType() {
    return FileReference.class;
  }
}
