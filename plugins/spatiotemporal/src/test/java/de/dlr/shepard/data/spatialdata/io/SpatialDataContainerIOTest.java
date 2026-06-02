package de.dlr.shepard.data.spatialdata.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SpatialDataContainerIO} including the
 * MFFD-SPATIAL-FRAME-HANDSHAKE additive {@code frameAppId} field.
 */
public class SpatialDataContainerIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(SpatialDataContainerIO.class).verify();
  }

  @Test
  public void fromEntity_carriesFrameAppId() {
    var container = new SpatialDataContainer(1L);
    container.setName("AFP-laser-profile");
    container.setFrameAppId("01931f0a-1234-7890-abcd-ef0123456789");

    var io = SpatialDataContainerIO.fromEntity(container);

    assertEquals("01931f0a-1234-7890-abcd-ef0123456789", io.getFrameAppId());
    assertEquals("AFP-laser-profile", io.getName());
  }

  @Test
  public void fromEntity_handlesNullFrameAppId() {
    var container = new SpatialDataContainer(2L);
    container.setName("legacy-container");

    var io = SpatialDataContainerIO.fromEntity(container);

    assertNull(io.getFrameAppId());
  }

  @Test
  public void jsonSerialization_omitsFrameAppIdWhenNull() throws Exception {
    // Wire-compat: legacy /shepard/api/spatialDataContainers must not surface
    // the new field when it's null (byte-identical to upstream).
    var container = new SpatialDataContainer(3L);
    container.setName("uno");
    var io = SpatialDataContainerIO.fromEntity(container);

    String json = new ObjectMapper().writeValueAsString(io);

    org.junit.jupiter.api.Assertions.assertFalse(
      json.contains("frameAppId"),
      "frameAppId must be omitted from the wire when null; got: " + json
    );
  }

  @Test
  public void jsonSerialization_includesFrameAppIdWhenSet() throws Exception {
    var container = new SpatialDataContainer(4L);
    container.setName("anchored");
    container.setFrameAppId("01931f0a-1234-7890-abcd-ef0123456789");
    var io = SpatialDataContainerIO.fromEntity(container);

    String json = new ObjectMapper().writeValueAsString(io);

    org.junit.jupiter.api.Assertions.assertTrue(
      json.contains("frameAppId"),
      "frameAppId must be on the wire when set; got: " + json
    );
    org.junit.jupiter.api.Assertions.assertTrue(
      json.contains("01931f0a-1234-7890-abcd-ef0123456789"),
      "frameAppId value must round-trip; got: " + json
    );
  }
}
