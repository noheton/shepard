package de.dlr.shepard.v2.dataobject.io;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-TYPED-PRED-ID — regression guard: {@code predecessorId} must not appear
 * in the serialized JSON output. It is a numeric Neo4j internal ID that violates
 * the v2 "no numeric internal IDs on the wire" contract.
 */
public class TypedPredecessorSummaryIOTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void predecessorId_isNotSerializedToWire() throws Exception {
    TypedPredecessorSummaryIO io = new TypedPredecessorSummaryIO(
      "01930a2b-fe4c-7e3c-9f1d-8a5b2c3d4e5f",
      42L,
      "TR-004",
      "PUBLISHED",
      "prov:wasRevisionOf"
    );

    String json = mapper.writeValueAsString(io);

    assertFalse(
      json.contains("predecessorId"),
      "predecessorId (numeric Neo4j ID) must not appear in serialized JSON — " +
      "it violates the v2 no-numeric-id contract. JSON was: " + json
    );
    assertTrue(
      json.contains("predecessorAppId"),
      "predecessorAppId must be present in serialized JSON. JSON was: " + json
    );
    assertTrue(
      json.contains("predecessorName"),
      "predecessorName must be present in serialized JSON. JSON was: " + json
    );
  }
}
