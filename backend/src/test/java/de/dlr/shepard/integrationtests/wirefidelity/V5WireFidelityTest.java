package de.dlr.shepard.integrationtests.wirefidelity;

import static io.restassured.RestAssured.given;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.dlr.shepard.integrationtests.BaseTestCaseIT;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Superclass for v5 wire-fidelity regression tests.
 *
 * <p>The {@code /shepard/api/...} surface is byte-frozen against upstream
 * {@code gitlab.com/dlr-shepard/shepard 5.2.0} (see {@code CLAUDE.md §"API-version policy"}
 * and {@code aidocs/34-upstream-upgrade-path.md}). Until this corpus shipped, that
 * constraint was enforced only by code-review eyeballs — too thin a guarantee. These
 * fixtures define the contract: every endpoint listed here must return the same key set,
 * the same types, and the same caller-provided values it returned when the fixture was
 * recorded.
 *
 * <h2>Why "v5" and not "v5.2.0"</h2>
 *
 * The fixtures track <em>the contract the fork's main keeps with upstream 5.2.0</em>. The
 * source of truth for recording is this fork's own main, because by the standing CLAUDE.md
 * rule the fork must already be wire-compatible. If a recorded shape ever diverges from
 * actual upstream 5.2.0, that's a fork bug — recorded as a separate row in
 * {@code aidocs/34-upstream-upgrade-path.md}, not patched in the fixture. The fixtures
 * are downstream of the policy, not upstream of it.
 *
 * <h2>How to add a fixture</h2>
 *
 * <ol>
 *   <li>Subclass {@link V5WireFidelityTest} (typically one subclass per entity kind —
 *       Collections, DataObjects, FileContainers, …). Annotate with
 *       {@code @QuarkusIntegrationTest @TestMethodOrder(MethodOrderer.OrderAnnotation.class)}.
 *   </li>
 *   <li>Write a JUnit {@code @Test} that calls {@link #assertWireMatches(String, Response)}
 *       passing a fixture id and the response from a deterministic request. Deterministic =
 *       caller-supplied values (name, description, attributes) are fixed so the recorded
 *       response is reproducible byte-for-byte modulo dynamic fields.</li>
 *   <li>Run once with {@code -Dshepard.fixtures.record=true} — the test framework writes the
 *       fixture file under {@code backend/src/test/resources/fixtures/v5/&lt;slug&gt;/}.</li>
 *   <li>Review the recorded JSON: every dynamic field (Neo4j {@code id}, mint {@code appId},
 *       {@code createdAt}, {@code createdBy}, …) should already be replaced with a
 *       {@link V5JsonNormalizer} sentinel (e.g. {@code &lt;&lt;ANY-LONG&gt;&gt;}).
 *       Add to {@link #DEFAULT_DYNAMIC_FIELDS} if you've discovered a new dynamic key.</li>
 *   <li>Run without the system property: the test now asserts the live response matches the
 *       recorded fixture.</li>
 * </ol>
 *
 * <h2>How to update a fixture (legitimate wire change)</h2>
 *
 * Wire changes on {@code /shepard/api/...} should be exceedingly rare — they break upstream
 * clients. If one is intentional and approved (e.g. a security fix that subtracts a leaked
 * field), re-record with {@code -Dshepard.fixtures.record=true} and call it out in the same
 * PR's {@code aidocs/34} row plus {@code docs/reference/v5-cross-instance-quirks.md} so the
 * delta is loud.
 */
public abstract class V5WireFidelityTest extends BaseTestCaseIT {

  /** System property that switches the suite from assert mode to record mode. */
  public static final String RECORD_PROPERTY = "shepard.fixtures.record";

  protected static final ObjectMapper MAPPER = new ObjectMapper()
    .enable(SerializationFeature.INDENT_OUTPUT)
    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  /**
   * Fields that the server always mints / overwrites. Subclasses can extend this via
   * {@link #dynamicFields()} for their entity-kind-specific keys.
   */
  protected static final Map<String, String> DEFAULT_DYNAMIC_FIELDS;

  static {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("id", V5JsonNormalizer.ANY_LONG);
    m.put("appId", V5JsonNormalizer.ANY_STRING);
    m.put("createdAt", V5JsonNormalizer.ANY_STRING);
    m.put("createdBy", V5JsonNormalizer.ANY_STRING);
    m.put("updatedAt", V5JsonNormalizer.NULL_OR_STRING);
    m.put("updatedBy", V5JsonNormalizer.NULL_OR_STRING);
    m.put("dataObjectId", V5JsonNormalizer.ANY_LONG);
    m.put("collectionId", V5JsonNormalizer.ANY_LONG);
    m.put("containerId", V5JsonNormalizer.ANY_LONG);
    m.put("dataObjectIds", V5JsonNormalizer.ANY_LONG_ARRAY);
    m.put("incomingIds", V5JsonNormalizer.ANY_LONG_ARRAY);
    m.put("defaultFileContainerId", V5JsonNormalizer.NULL_OR_STRING);
    m.put("predecessorId", V5JsonNormalizer.NULL_OR_STRING);
    m.put("successorId", V5JsonNormalizer.NULL_OR_STRING);
    DEFAULT_DYNAMIC_FIELDS = Map.copyOf(m);
  }

  /**
   * Entity-kind-specific dynamic fields. Override to add (e.g. {@code uid} on FileReference,
   * {@code measurements} on TimeseriesContainer, …). Default = {@link #DEFAULT_DYNAMIC_FIELDS}.
   */
  protected Map<String, String> dynamicFields() {
    return DEFAULT_DYNAMIC_FIELDS;
  }

  /** Fixture-corpus root, relative to the project. */
  protected Path fixtureRoot() {
    return Path.of("src", "test", "resources", "fixtures", "v5");
  }

  /**
   * Assert (or record) that the {@code actual} response matches the fixture at
   * {@code <fixtureRoot>/<slug>/<fixtureId>.response.json}.
   *
   * <p>In record mode ({@link #RECORD_PROPERTY}), normalises the response (replacing
   * {@link #dynamicFields()} values with sentinels), writes it to disk, and skips the
   * assertion. In assert mode, parses the recorded fixture and calls
   * {@link V5JsonNormalizer#assertMatches(JsonNode, JsonNode)}.
   *
   * @param slug entity-kind subdirectory: {@code collections}, {@code dataobjects},
   *     {@code filecontainers}, …
   * @param fixtureId fixture file basename without extension
   * @param actual restassured response from the live backend
   */
  protected void assertWireMatches(String slug, String fixtureId, Response actual) {
    try {
      JsonNode actualJson = MAPPER.readTree(actual.asString());
      Path fixturePath = fixtureRoot().resolve(slug).resolve(fixtureId + ".response.json");
      if (Boolean.parseBoolean(System.getProperty(RECORD_PROPERTY, "false"))) {
        recordFixture(fixturePath, actualJson);
        return;
      }
      if (!Files.exists(fixturePath)) {
        throw new AssertionError(
          "No v5 fixture at " + fixturePath +
            ". Re-run with -D" + RECORD_PROPERTY + "=true to record it."
        );
      }
      JsonNode expected = MAPPER.readTree(Files.readString(fixturePath));
      V5JsonNormalizer.assertMatches(expected, actualJson);
    } catch (IOException e) {
      throw new AssertionError("Failed to read fixture: " + e, e);
    }
  }

  private void recordFixture(Path fixturePath, JsonNode actualJson) throws IOException {
    Files.createDirectories(fixturePath.getParent());
    JsonNode normalised = V5JsonNormalizer.redactDynamicFields(MAPPER, actualJson, dynamicFields());
    String pretty = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(normalised);
    Files.writeString(fixturePath, pretty + System.lineSeparator());
  }

  /**
   * Convenience: GET {@code endpoint} with the default user and return the response.
   */
  protected Response get(String endpoint) {
    return given().spec(requestSpecOfDefaultUser).when().get(endpoint).then().extract().response();
  }

  /**
   * Convenience: POST {@code endpoint} with a JSON body and the default user.
   */
  protected Response post(String endpoint, Object body) {
    RequestSpecification spec = requestSpecOfDefaultUser;
    return given().spec(spec).body(body).when().post(endpoint).then().extract().response();
  }

  /**
   * Helper: build a request body as a key-sorted map. Sorted-by-key so that the
   * request JSON is byte-stable across recordings (Jackson preserves the iteration order
   * of the map it serialises).
   */
  protected static Map<String, Object> sortedBody(Map<String, Object> body) {
    return new TreeMap<>(body);
  }
}
