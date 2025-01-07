package de.dlr.shepard.data.timeseries.migration.influxtimeseries;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class InfluxTimeseriesTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(InfluxTimeseries.class).verify();
  }

  @Test
  public void getUniqueIdTest() {
    var ts = new InfluxTimeseries("meas", "dev", "loc", "sym", "field");
    var actual = ts.getUniqueId();
    var expected = "meas-dev-loc-sym-field";

    assertEquals(expected, actual);
  }
}
