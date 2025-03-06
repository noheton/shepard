package de.dlr.shepard.context.references.spatialdata.daos;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.context.references.spatialdata.entities.SpatialDataReference;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

@RequestScoped
public class SpatialDataReferenceDAO extends VersionableEntityDAO<SpatialDataReference> {

  public List<SpatialDataReference> findByDataObjectShepardId(long dataObjectShepardId) {
    String query =
      String.format(
        "MATCH (d:DataObject {shepardId: %d})-[hr:has_reference]->%s",
        dataObjectShepardId,
        CypherQueryHelper.getObjectPart("r", "SpatialDataReference", false)
      ) +
      CypherQueryHelper.getReturnPart("r");
    var queryResult = findByQuery(query, Collections.emptyMap());

    List<SpatialDataReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null && r.getDataObject().getShepardId().equals(dataObjectShepardId))
      .toList();

    return result;
  }

  @Override
  public Class<SpatialDataReference> getEntityType() {
    return SpatialDataReference.class;
  }
}
