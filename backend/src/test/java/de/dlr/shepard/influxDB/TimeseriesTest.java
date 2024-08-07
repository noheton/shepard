package de.dlr.shepard.influxDB;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class TimeseriesTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(Timeseries.class).verify();
  }

  @Test
  public void getUniqueIdTest() {
    var ts = new Timeseries("meas", "dev", "loc", "sym", "field");
    var actual = ts.getUniqueId();
    var expected = "meas-dev-loc-sym-field";

    assertEquals(expected, actual);
  }
}
