package de.dlr.shepard.context.references.timeseriesreference.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.data.timeseries.model.TimeseriesFiveTuple;
import jakarta.enterprise.context.RequestScoped;
import java.util.Map;

@RequestScoped
public class TimeseriesFiveTupleDAO extends GenericDAO<TimeseriesFiveTuple> {

  /**
   * Find a timeseries by properties
   *
   * @param measurement  measurement
   * @param device       device
   * @param location     location
   * @param symbolicName symbolicName
   * @param field        field
   *
   * @return the found timeseries or null
   */
  public TimeseriesFiveTuple find(
    String measurement,
    String device,
    String location,
    String symbolicName,
    String field
  ) {
    var query =
      "MATCH (t:TimeseriesFiveTuple { measurement: $measurement, device: $device, location: $location, symbolicName: $symbolicName, field: $field }) %s".formatted(
          CypherQueryHelper.getReturnPart("t")
        );
    Map<String, Object> params = Map.of(
      "measurement",
      measurement,
      "device",
      device,
      "location",
      location,
      "symbolicName",
      symbolicName,
      "field",
      field
    );
    var results = findByQuery(query, params);
    return results.iterator().hasNext() ? results.iterator().next() : null;
  }

  @Override
  public Class<TimeseriesFiveTuple> getEntityType() {
    return TimeseriesFiveTuple.class;
  }
}
