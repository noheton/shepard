package de.dlr.shepard.context.references.timeseriesreference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesTupleDAO;
import de.dlr.shepard.data.timeseries.model.TimeseriesTuple;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class TimeseriesTupleDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private TimeseriesTupleDAO dao = new TimeseriesTupleDAO();

  @Test
  public void findTest() {
    var ts = new TimeseriesTuple("meas", "dev", "loc", "symName", "value");
    var query =
      """
      MATCH (t:TimeseriesTuple { measurement: $measurement, device: $device, location: $location, symbolicName: $symbolicName, field: $field }) \
      MATCH path=(t)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN t, nodes(path), relationships(path)""";

    when(
      session.query(
        TimeseriesTuple.class,
        query,
        Map.of("measurement", "meas", "device", "dev", "location", "loc", "symbolicName", "symName", "field", "value")
      )
    ).thenReturn(List.of(ts));
    var actual = dao.find("meas", "dev", "loc", "symName", "value");
    assertEquals(ts, actual);
  }

  @Test
  public void findTest_notFound() {
    var query =
      """
      MATCH (t:TimeseriesTuple { measurement: $measurement, device: $device, location: $location, symbolicName: $symbolicName, field: $field }) \
      MATCH path=(t)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN t, nodes(path), relationships(path)""";

    when(
      session.query(
        TimeseriesTuple.class,
        query,
        Map.of("measurement", "meas", "device", "dev", "location", "loc", "symbolicName", "symName", "field", "value")
      )
    ).thenReturn(Collections.emptyList());
    var actual = dao.find("meas", "dev", "loc", "symName", "value");
    assertNull(actual);
  }
}
