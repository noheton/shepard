package de.dlr.shepard.context.references.structureddata.daos;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.TraversalRules;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

@RequestScoped
public class StructuredDataReferenceDAO extends VersionableEntityDAO<StructuredDataReference> {

  /**
   * Searches the database for references.
   *
   * @param dataObjectId identifies the dataObject
   * @return a List of references
   */
  public List<StructuredDataReference> findByDataObjectNeo4jId(long dataObjectId) {
    String query =
      "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE ID(d)=%d ".formatted(
          CypherQueryHelper.getObjectPart("r", "StructuredDataReference", false),
          dataObjectId
        ) +
      CypherQueryHelper.getReturnPart("r");
    var queryResult = findByQuery(query, Collections.emptyMap());
    List<StructuredDataReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getId().equals(dataObjectId))
      .toList();
    return result;
  }

  public List<StructuredDataReference> findReachableReferencesByShepardId(
    TraversalRules traversalRule,
    long collectionShepardId,
    long startShepardId,
    String userName
  ) {
    String query = getSearchForReachableReferencesByShepardIdQuery(
      traversalRule,
      collectionShepardId,
      startShepardId,
      userName
    );
    var queryResult = findByQuery(query, Collections.emptyMap());
    List<StructuredDataReference> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  public List<StructuredDataReference> findReachableReferencesByNeo4jId(
    TraversalRules traversalRule,
    long collectionShepardId,
    long startShepardId,
    String userName
  ) {
    String query = getSearchForReachableReferencesByNeo4jIdQuery(
      traversalRule,
      collectionShepardId,
      startShepardId,
      userName
    );
    var queryResult = findByQuery(query, Collections.emptyMap());
    List<StructuredDataReference> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  public List<StructuredDataReference> findReachableReferencesByNeo4jId(
    long collectionId,
    long startId,
    String userName
  ) {
    String query = getSearchForReachableReferencesQuery(collectionId, startId, userName);
    var queryResult = findByQuery(query, Collections.emptyMap());
    List<StructuredDataReference> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  public List<StructuredDataReference> findReachableReferencesByShepardId(
    long collectionShepardId,
    long startShepardId,
    String userName
  ) {
    String query = getSearchForReachableReferencesByShepardIdQuery(collectionShepardId, startShepardId, userName);
    var queryResult = findByQuery(query, Collections.emptyMap());
    List<StructuredDataReference> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  public List<StructuredDataReference> findReachableReferencesByNeo4jId(long collectionId, String userName) {
    String query = getSearchForReachableReferencesQuery(collectionId, userName);
    var queryResult = findByQuery(query, Collections.emptyMap());
    List<StructuredDataReference> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  public List<StructuredDataReference> findReachableReferencesByShepardId(long collectionShepardId, String userName) {
    String query = getSearchForReachableReferencesByShepardIdQuery(collectionShepardId, userName);
    var queryResult = findByQuery(query, Collections.emptyMap());
    List<StructuredDataReference> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  public List<StructuredDataReference> findByDataObjectShepardId(long dataObjectShepardId) {
    String query =
      String.format(
        "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d." + Constants.SHEPARD_ID + "=%d ",
        CypherQueryHelper.getObjectPart("r", "StructuredDataReference", false),
        dataObjectShepardId
      ) +
      CypherQueryHelper.getReturnPart("r");
    var queryResult = findByQuery(query, Collections.emptyMap());
    List<StructuredDataReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getShepardId().equals(dataObjectShepardId))
      .toList();
    return result;
  }

  @Override
  public Class<StructuredDataReference> getEntityType() {
    return StructuredDataReference.class;
  }
}
