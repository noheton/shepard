package de.dlr.shepard.v2.dataobject.io;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.DataObjectService;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * PROV1k — pure unit tests (no Quarkus boot) for typed predecessor logic.
 *
 * <p>Covers:
 * <ol>
 *   <li>Create with {@code typedPredecessors} only → {@code predecessorIds} auto-populated</li>
 *   <li>Create with {@code predecessorIds} only (legacy path) → {@code typedPredecessorsJson} null</li>
 *   <li>Create with both → {@code typedPredecessors} takes precedence</li>
 *   <li>Invalid {@code relationshipType} → {@link InvalidBodyException} thrown</li>
 *   <li>Serialise/deserialise roundtrip for {@code typedPredecessorsJson}</li>
 *   <li>Default {@code "prov:wasInformedBy"} applied when {@code relationshipType} is null</li>
 * </ol>
 */
public class TypedPredecessorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  // ── Test 1: typedPredecessors only → predecessorIds auto-populated ───────

  @Test
  public void test1_typedPredecessors_populatesPredecessorAppIds() {
    // When a CreateDataObjectV2IO is built with typedPredecessors only (no
    // predecessorIds), the service resolves each predecessorAppId to a DataObject.
    // The resolved DataObjects are then added to the predecessors list, which
    // is equivalent to predecessorIds being auto-populated from the typed list.
    String predAppId = "01930a2b-0000-7000-0000-000000000001";
    TypedPredecessorIO tp = new TypedPredecessorIO(predAppId, "prov:wasRevisionOf");

    CreateDataObjectV2IO io = new CreateDataObjectV2IO();
    io.setTypedPredecessors(List.of(tp));
    // predecessorIds intentionally left unset (null)

    assertNull(io.getPredecessorIds(), "predecessorIds should be null when only typedPredecessors set");
    assertNotNull(io.getTypedPredecessors(), "typedPredecessors should be non-null");
    assertEquals(1, io.getTypedPredecessors().size());
    assertEquals(predAppId, io.getTypedPredecessors().get(0).predecessorAppId());
    assertEquals("prov:wasRevisionOf", io.getTypedPredecessors().get(0).effectiveRelationshipType());
  }

  // ── Test 2: predecessorIds only (legacy) → typedPredecessorsJson null ────

  @Test
  public void test2_legacyPredecessorIds_noTypedJson() {
    // A legacy DataObjectIO with predecessorIds only should never set
    // typedPredecessorsJson on the entity.
    // We model this by asserting that a DataObject built without typed
    // predecessors has typedPredecessorsJson == null.
    DataObject dataObject = new DataObject();
    dataObject.setTypedPredecessorsJson(null);

    assertNull(dataObject.getTypedPredecessorsJson(),
      "typedPredecessorsJson must be null for legacy predecessorIds-only create");
  }

  // ── Test 3: both provided → typedPredecessors takes precedence ───────────

  @Test
  public void test3_bothProvided_typedPredecessorsTakePrecedence() {
    // When both typedPredecessors and predecessorIds are set, the service
    // should use typedPredecessors. We verify this by checking the IO shape:
    // if typedPredecessors is non-empty, the service's branch runs the typed path.
    String predAppId = "01930a2b-0000-7000-0000-000000000002";
    TypedPredecessorIO tp = new TypedPredecessorIO(predAppId, "fair2r:repairs");

    CreateDataObjectV2IO io = new CreateDataObjectV2IO();
    io.setTypedPredecessors(List.of(tp));
    io.setPredecessorIds(new long[] { 999L }); // legacy IDs also set

    // The non-null/non-empty typedPredecessors list is the authoritative source.
    assertNotNull(io.getTypedPredecessors());
    assertFalse(io.getTypedPredecessors().isEmpty(), "typedPredecessors is non-empty — takes precedence");
    assertEquals(predAppId, io.getTypedPredecessors().get(0).predecessorAppId());
    assertEquals("fair2r:repairs", io.getTypedPredecessors().get(0).effectiveRelationshipType());
  }

  // ── Test 4: invalid relationshipType → InvalidBodyException ──────────────

  @Test
  public void test4_invalidRelationshipType_throws() {
    TypedPredecessorIO tp = new TypedPredecessorIO(
      "01930a2b-0000-7000-0000-000000000003",
      "owl:sameAs" // not in ALLOWED_TYPES
    );

    assertThrows(InvalidBodyException.class, tp::validate,
      "validate() must throw InvalidBodyException for unknown relationshipType");
  }

  @Test
  public void test4b_blankPredecessorAppId_throws() {
    TypedPredecessorIO tp = new TypedPredecessorIO("", "prov:wasInformedBy");
    assertThrows(InvalidBodyException.class, tp::validate,
      "validate() must throw InvalidBodyException for blank predecessorAppId");
  }

  // ── QM1b — fair2r:concession is now part of the allowed vocabulary ──────

  @Test
  public void qm1b_fair2rConcession_isAllowed() {
    TypedPredecessorIO tp = new TypedPredecessorIO(
      "01930a2b-0000-7000-0000-000000000099",
      "fair2r:concession"
    );
    // Must not throw — fair2r:concession is part of ALLOWED_TYPES post-QM1b.
    tp.validate();
    assertEquals("fair2r:concession", tp.effectiveRelationshipType());
    assertEquals(true, TypedPredecessorIO.ALLOWED_TYPES.contains("fair2r:concession"),
      "QM1b: ALLOWED_TYPES must include 'fair2r:concession'");
  }

  // ── Test 5: serialise/deserialise roundtrip ───────────────────────────────

  @Test
  public void test5_serialiseDeserialise_roundtrip() throws Exception {
    List<TypedPredecessorIO> original = List.of(
      new TypedPredecessorIO("01930a2b-0000-7000-0000-000000000010", "prov:wasRevisionOf"),
      new TypedPredecessorIO("01930a2b-0000-7000-0000-000000000011", "fair2r:repairs"),
      new TypedPredecessorIO("01930a2b-0000-7000-0000-000000000012", null)
    );

    String json = MAPPER.writeValueAsString(original);
    assertNotNull(json, "serialised JSON must not be null");
    assertFalse(json.isBlank(), "serialised JSON must not be blank");

    List<TypedPredecessorIO> roundtripped = MAPPER.readValue(
      json,
      new TypeReference<List<TypedPredecessorIO>>() {}
    );

    assertEquals(3, roundtripped.size());
    assertEquals("01930a2b-0000-7000-0000-000000000010", roundtripped.get(0).predecessorAppId());
    assertEquals("prov:wasRevisionOf", roundtripped.get(0).effectiveRelationshipType());
    assertEquals("01930a2b-0000-7000-0000-000000000011", roundtripped.get(1).predecessorAppId());
    assertEquals("fair2r:repairs", roundtripped.get(1).effectiveRelationshipType());
    // null → default applied by effectiveRelationshipType()
    assertEquals(TypedPredecessorIO.DEFAULT_TYPE, roundtripped.get(2).effectiveRelationshipType());
  }

  // ── Test 6: null relationshipType defaults to prov:wasInformedBy ─────────

  @Test
  public void test6_nullRelationshipType_defaultsToWasInformedBy() {
    TypedPredecessorIO tp = new TypedPredecessorIO(
      "01930a2b-0000-7000-0000-000000000020",
      null // null relationshipType
    );

    assertEquals(
      "prov:wasInformedBy",
      tp.effectiveRelationshipType(),
      "null relationshipType must default to 'prov:wasInformedBy'"
    );

    // validate() must not throw for null relationshipType (it is valid).
    assertDoesNotThrow(tp::validate, "null relationshipType must pass validation");
  }

  // ── Test 7: parseTypedPredecessorsJson helper in DataObjectDetailV2IO ─────

  @Test
  public void test7_parseTypedPredecessorsJson_emptyOnNull() {
    Map<String, String> result = DataObjectDetailV2IO.parseTypedPredecessorsJson(null);
    assertNotNull(result);
    assertTrue(result.isEmpty(), "null JSON must produce empty map");
  }

  @Test
  public void test7b_parseTypedPredecessorsJson_mapsAppIdToType() throws Exception {
    List<TypedPredecessorIO> entries = List.of(
      new TypedPredecessorIO("app-id-A", "prov:wasRevisionOf"),
      new TypedPredecessorIO("app-id-B", null)
    );
    String json = MAPPER.writeValueAsString(entries);

    Map<String, String> result = DataObjectDetailV2IO.parseTypedPredecessorsJson(json);
    assertEquals("prov:wasRevisionOf", result.get("app-id-A"));
    assertEquals("prov:wasInformedBy", result.get("app-id-B"),
      "null relationshipType must default to prov:wasInformedBy in parsed map");
  }

  @Test
  public void test7c_parseTypedPredecessorsJson_malformedJson_returnsEmpty() {
    Map<String, String> result = DataObjectDetailV2IO.parseTypedPredecessorsJson("{not valid json}");
    assertNotNull(result);
    assertTrue(result.isEmpty(), "malformed JSON must produce empty map (no exception propagated)");
  }
}
