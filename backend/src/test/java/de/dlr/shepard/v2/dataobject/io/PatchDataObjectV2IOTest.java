package de.dlr.shepard.v2.dataobject.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import org.junit.jupiter.api.Test;

/**
 * BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH — unit tests for {@link PatchDataObjectV2IO}.
 */
class PatchDataObjectV2IOTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static DataObject stubDataObject() {
    Collection coll = new Collection();
    coll.setShepardId(1L);
    DataObject d = new DataObject();
    d.setShepardId(42L);
    d.setAppId("018f9c5a-7e26-7000-a000-000000000001");
    d.setName("sensor-track");
    d.setCollection(coll);
    return d;
  }

  @Test
  void predecessorAppIds_isNullByDefault() {
    PatchDataObjectV2IO io = new PatchDataObjectV2IO();
    assertNull(io.getPredecessorAppIds(),
      "predecessorAppIds must default to null so absent-from-patch means no override");
  }

  @Test
  void constructorFromDataObject_doesNotPopulatePredecessorAppIds() {
    PatchDataObjectV2IO io = new PatchDataObjectV2IO(stubDataObject());
    assertNull(io.getPredecessorAppIds(),
      "Constructing from a DataObject must not populate predecessorAppIds " +
      "(it is only set when the caller includes it in the merge-patch body)");
  }

  @Test
  void predecessorAppIds_canBeSetAndRetrieved() {
    PatchDataObjectV2IO io = new PatchDataObjectV2IO();
    String[] appIds = {"app-1", "app-2"};
    io.setPredecessorAppIds(appIds);
    assertArrayEquals(appIds, io.getPredecessorAppIds());
  }

  @Test
  void predecessorAppIds_deserializesFromJson() throws Exception {
    String json = "{\"predecessorAppIds\":[\"abc-uuid\",\"def-uuid\"]}";
    PatchDataObjectV2IO io = MAPPER.readValue(json, PatchDataObjectV2IO.class);
    assertArrayEquals(new String[]{"abc-uuid", "def-uuid"}, io.getPredecessorAppIds());
  }

  @Test
  void predecessorAppIds_absentInJsonLeavesNull() throws Exception {
    String json = "{\"name\":\"renamed\"}";
    PatchDataObjectV2IO io = MAPPER.readValue(json, PatchDataObjectV2IO.class);
    assertNull(io.getPredecessorAppIds(),
      "Field absent from JSON must remain null (merge-patch semantics: no predecessor override)");
  }

  @Test
  void predecessorAppIds_emptyArrayDeserializes() throws Exception {
    String json = "{\"predecessorAppIds\":[]}";
    PatchDataObjectV2IO io = MAPPER.readValue(json, PatchDataObjectV2IO.class);
    assertArrayEquals(new String[]{}, io.getPredecessorAppIds(),
      "Empty array must deserialize as empty (signals 'clear all predecessors')");
  }
}
