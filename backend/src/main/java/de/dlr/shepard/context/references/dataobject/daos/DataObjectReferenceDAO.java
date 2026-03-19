package de.dlr.shepard.context.references.dataobject.daos;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

@RequestScoped
public class DataObjectReferenceDAO extends VersionableEntityDAO<DataObjectReference> {

  /**
   * Searches the database for references.
   *
   * @param dataObjectId identifies the dataObject
   * @return a List of references
   */
  public List<DataObjectReference> findByDataObjectNeo4jId(long dataObjectId) {
    String query =
      "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE ID(d)=%d ".formatted(
          CypherQueryHelper.getObjectPart("r", "DataObjectReference", false),
          dataObjectId
        ) +
      CypherQueryHelper.getReturnPart("r");

    var queryResult = findByQuery(query, Collections.emptyMap());

    List<DataObjectReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getId().equals(dataObjectId))
      .toList();

    return result;
  }

  public List<DataObjectReference> findByDataObjectShepardId(long dataObjectShepardId, UUID versionUID) {
    String query =
      String.format(
        "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d." + Constants.SHEPARD_ID + "=%d ",
        CypherQueryHelper.getObjectPart("r", "DataObjectReference", false),
        dataObjectShepardId
      ) +
      CypherQueryHelper.getReturnPart("r");
    StringBuffer queryBuffer = new StringBuffer();
    queryBuffer.append("MATCH (v:Version)<-[:has_version]-(d:DataObject)-[hr:has_reference]->");
    queryBuffer.append(CypherQueryHelper.getObjectPart("r", "DataObjectReference", false));
    queryBuffer.append(" WHERE d." + Constants.SHEPARD_ID + "=" + dataObjectShepardId + " AND ");
    if (versionUID == null) queryBuffer.append(CypherQueryHelper.getVersionHeadPart("v"));
    else queryBuffer.append(CypherQueryHelper.getVersionPart("v", versionUID));
    queryBuffer.append(" " + CypherQueryHelper.getReturnPart("r"));
    query = queryBuffer.toString();
    var queryResult = findByQuery(query, Collections.emptyMap());
    List<DataObjectReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getShepardId().equals(dataObjectShepardId))
      .toList();
    return result;
  }

  @Override
  public Class<DataObjectReference> getEntityType() {
    return DataObjectReference.class;
  }
}
