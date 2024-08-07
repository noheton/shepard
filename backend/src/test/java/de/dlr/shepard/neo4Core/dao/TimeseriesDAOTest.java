package de.dlr.shepard.neo4Core.dao;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.influxDB.Timeseries;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class TimeseriesDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private TimeseriesDAO dao = new TimeseriesDAO();

  @Test
  public void findTest() {
    var ts = new Timeseries("meas", "dev", "loc", "symName", "value");
    var query =
      """
      MATCH (t:Timeseries { measurement: $measurement, device: $device, location: $location, symbolicName: $symbolicName, field: $field }) \
      MATCH path=(t)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN t, nodes(path), relationships(path)""";

    when(
      session.query(
        Timeseries.class,
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
      MATCH (t:Timeseries { measurement: $measurement, device: $device, location: $location, symbolicName: $symbolicName, field: $field }) \
      MATCH path=(t)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN t, nodes(path), relationships(path)""";

    when(
      session.query(
        Timeseries.class,
        query,
        Map.of("measurement", "meas", "device", "dev", "location", "loc", "symbolicName", "symName", "field", "value")
      )
    ).thenReturn(Collections.emptyList());
    var actual = dao.find("meas", "dev", "loc", "symName", "value");
    assertNull(actual);
  }
}
