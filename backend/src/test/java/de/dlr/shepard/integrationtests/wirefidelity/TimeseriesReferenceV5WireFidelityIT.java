package de.dlr.shepard.integrationtests.wirefidelity;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * v5 wire-fidelity test for {@code /shepard/api/.../timeseriesReferences}.
 *
 * <p>Exercises the full {@code BasicReferenceIO + VersionableEntity + start/end/timeseries[] +
 * timeseriesContainerId} shape. The five fork-additive fields ({@code qualityScore},
 * {@code lastScoredAt}, {@code timeReference}, {@code wallClockOffset},
 * {@code wallClockOffsetSource}) are all {@code @JsonInclude(NON_NULL)} and are absent on a
 * freshly created reference, so they do not appear in the fixture — confirming upstream
 * byte-compatibility.
 *
 * <p>Requires Timescale data insertion (32 synthetic data points) as noted in
 * {@code docs/reference/v5-cross-instance-quirks.md §Endpoint coverage — Gaps}.
 */
@QuarkusIntegrationTest
public class TimeseriesReferenceV5WireFidelityIT extends V5WireFidelityTest {

  private static final String SLUG = "timeseriesreferences";
  private static final int NUM_POINTS = 32;

  private static CollectionIO collection;
  private static DataObjectIO dataObject;
  private static TimeseriesContainerIO container;
  private static long startNanos;
  private static long endNanos;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("TimeseriesReferenceWireFixture_" + System.currentTimeMillis());
    dataObject = createDataObject("TimeseriesReferenceWireFixtureDO", collection.getId());

    var containerIO = new TimeseriesContainerIO();
    containerIO.setName("TimeseriesReferenceWireFixtureContainer");
    container = given()
      .spec(requestSpecOfDefaultUser)
      .body(containerIO)
      .when()
      .post("/" + Constants.TIMESERIES_CONTAINERS)
      .then()
      .statusCode(201)
      .extract()
      .as(TimeseriesContainerIO.class);

    var currentNanos = System.currentTimeMillis() * 1_000_000L;
    var slice = (2f * Math.PI) / (NUM_POINTS - 1);
    List<TimeseriesDataPoint> points = new java.util.ArrayList<>();
    for (int i = 0; i < NUM_POINTS; i++) {
      long offset = (long) i * 1_000_000_000L;
      points.add(new TimeseriesDataPoint(currentNanos + offset, Math.sin(slice * i)));
    }
    var ts = new Timeseries("meas", "dev", "loc", "symName", "field");
    var payload = new TimeseriesWithDataPoints(ts, points);

    given()
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .when()
      .post("/" + Constants.TIMESERIES_CONTAINERS + "/" + container.getId() + "/" + Constants.PAYLOAD)
      .then()
      .statusCode(201);

    startNanos = currentNanos - 1_000_000_000L;
    endNanos = currentNanos + (long) NUM_POINTS * 1_000_000_000L;
  }

  /**
   * Additional dynamic fields: {@code start} and {@code end} are caller-supplied longs
   * that are computed from {@code System.currentTimeMillis()} at test time — they must be
   * treated as dynamic to keep the fixture stable across runs.
   */
  @Override
  protected Map<String, String> dynamicFields() {
    Map<String, String> m = new LinkedHashMap<>(DEFAULT_DYNAMIC_FIELDS);
    m.put("start", V5JsonNormalizer.ANY_LONG);
    m.put("end", V5JsonNormalizer.ANY_LONG);
    m.put("timeseriesContainerId", V5JsonNormalizer.ANY_LONG);
    return m;
  }

  @Test
  public void createTimeseriesReference_wireMatchesFixture() {
    var ref = new TimeseriesReferenceIO();
    ref.setName("TimeseriesReferenceWireFixture");
    ref.setStart(startNanos);
    ref.setEnd(endNanos);
    ref.setTimeseries(List.of(new Timeseries("meas", "dev", "loc", "symName", "field")));
    ref.setTimeseriesContainerId(container.getId());

    String url = "/%s/%d/%s/%d/%s".formatted(
      Constants.COLLECTIONS,
      collection.getId(),
      Constants.DATA_OBJECTS,
      dataObject.getId(),
      Constants.TIMESERIES_REFERENCES
    );

    Response response = given()
      .spec(requestSpecOfDefaultUser)
      .body(ref)
      .when()
      .post(url)
      .then()
      .statusCode(201)
      .extract()
      .response();

    assertWireMatches(SLUG, "create", response);
  }
}
