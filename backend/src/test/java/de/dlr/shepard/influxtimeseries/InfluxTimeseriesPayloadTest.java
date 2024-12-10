package de.dlr.shepard.influxtimeseries;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class InfluxTimeseriesPayloadTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(InfluxTimeseriesPayload.class).verify();
  }
}
