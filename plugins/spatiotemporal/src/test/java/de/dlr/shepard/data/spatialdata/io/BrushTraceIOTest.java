package de.dlr.shepard.data.spatialdata.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Brush-trace wire-shape tests for MFFD-SPATIAL-LINESCAN-IMPORTER-1.
 *
 * <p>The line-scan importer creates one {@link SpatialDataContainer} per
 * {@code TPS raw data.N} PNG chunk and uploads one {@link SpatialDataPointIO}
 * per row, carrying the full intensity vector in
 * {@code measurements.intensities}. These tests pin the IO-level contract
 * (field shapes + JSON round-trip + the brush-trace specific measurement
 * keys) so a refactor of the spatial substrate cannot silently drop the
 * line-scan shape on the floor.
 *
 * <p>Extends the existing {@link SpatialDataContainerIOTest} which covers
 * the {@code frameAppId} additive field; this class adds the brush-trace
 * payload contract on top.
 */
public class BrushTraceIOTest {

  private static final String INTENSITIES_KEY = "intensities";
  private static final String ROW_INDEX_KEY = "row_index";
  private static final String CHUNK_INDEX_KEY = "chunk_index";
  private static final String KIND_KEY = "kind";
  private static final String BRUSH_TRACE = "brush-trace";

  /**
   * The Spatial container that anchors a line-scan promotion carries the same
   * additive {@code frameAppId} as the pointcloud / trajectory siblings — the
   * brush-trace shape does not require a new column on the container side.
   */
  @Test
  public void container_carriesFrameAppId_forBrushTrace() {
    var container = new SpatialDataContainer(1L);
    container.setName("Track_66__Run_23133_/TPS raw data.18");
    container.setFrameAppId("01931f0a-1234-7890-abcd-ef0123456789");

    var io = SpatialDataContainerIO.fromEntity(container);

    assertEquals("01931f0a-1234-7890-abcd-ef0123456789", io.getFrameAppId());
    assertTrue(io.getName().contains("TPS raw data"));
  }

  /**
   * One brush-trace row's wire shape: timestamp + (x,y,z) + measurements
   * containing the intensity vector and row/chunk index. JSON must serialise
   * the array back round-trippable.
   */
  @Test
  public void payloadPoint_roundTripsIntensityVectorViaJsonNode() throws Exception {
    var intensities = List.of(7, 4, 6, 7, 4, 5, 6, 4, 6, 5);
    var point = new SpatialDataPointIO(
      1_700_000_000_000_000_000L,
      646.0, // column centroid for a 1292-wide row
      0.0, // row index
      0.0,
      Map.of(),
      Map.of(
        INTENSITIES_KEY,
        intensities,
        ROW_INDEX_KEY,
        0,
        CHUNK_INDEX_KEY,
        18,
        KIND_KEY,
        BRUSH_TRACE
      )
    );

    var mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(point);
    // The SpatialDataPointIO lacks a no-arg deserialisation constructor (it is
    // a write-shape only); we round-trip via the generic Map type instead,
    // which is what every JSON-aware consumer of /payload sees on the wire.
    Map<String, Object> back = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});

    assertEquals(1_700_000_000_000_000_000L, ((Number) back.get("timestamp")).longValue());
    assertEquals(646.0, ((Number) back.get("x")).doubleValue());
    assertEquals(0.0, ((Number) back.get("y")).doubleValue());
    assertEquals(0.0, ((Number) back.get("z")).doubleValue());

    @SuppressWarnings("unchecked")
    Map<String, Object> measurements = (Map<String, Object>) back.get("measurements");
    assertEquals(BRUSH_TRACE, measurements.get(KIND_KEY));
    assertEquals(18, ((Number) measurements.get(CHUNK_INDEX_KEY)).intValue());
    assertEquals(0, ((Number) measurements.get(ROW_INDEX_KEY)).intValue());

    var roundTripped = mapper.convertValue(
      measurements.get(INTENSITIES_KEY),
      new TypeReference<List<Integer>>() {}
    );
    assertEquals(intensities, roundTripped);
  }

  /**
   * The JSONB shape must accept the full 1292-wide intensity vector without
   * truncation. Sizing this at exactly the observed width gives an explicit
   * regression target if the wire format ever caps at 1024 / 2048 / etc.
   */
  @Test
  public void payloadPoint_acceptsFullWidthIntensityVector() throws Exception {
    int[] raw = new int[1292];
    // Sentinel: first pixel = 0, last pixel = 255 — exercises both bookends.
    // Middle elements get a non-uniform fingerprint so a silent truncate cannot
    // pass by coincidence.
    raw[0] = 0;
    raw[raw.length - 1] = 255;
    for (int i = 1; i < raw.length - 1; i++) {
      raw[i] = (i % 200);
    }
    var intensities = java.util.stream.IntStream.of(raw).boxed().toList();
    var point = new SpatialDataPointIO(
      1L,
      646.0,
      0.0,
      0.0,
      Map.of(),
      Map.of(INTENSITIES_KEY, intensities, ROW_INDEX_KEY, 0, KIND_KEY, BRUSH_TRACE)
    );

    var mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(point);
    Map<String, Object> back = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    @SuppressWarnings("unchecked")
    Map<String, Object> measurements = (Map<String, Object>) back.get("measurements");
    var roundTripped = mapper.convertValue(
      measurements.get(INTENSITIES_KEY),
      new TypeReference<List<Integer>>() {}
    );

    assertEquals(1292, roundTripped.size(), "1292-wide vector must survive round-trip");
    assertEquals(0, roundTripped.get(0));
    assertEquals(255, roundTripped.get(1291));
  }

  /**
   * The renderer relies on {@code measurements.kind == "brush-trace"} to pick
   * the heatmap branch instead of pointcloud/trajectory. Pin this value to
   * catch typos at the IO seam.
   */
  @Test
  public void payloadPoint_kindMarkerIsBrushTrace() throws Exception {
    var point = new SpatialDataPointIO(
      1L,
      0.0,
      0.0,
      0.0,
      Map.of(),
      Map.of(KIND_KEY, BRUSH_TRACE, INTENSITIES_KEY, List.of(1, 2, 3))
    );
    String json = new ObjectMapper().writeValueAsString(point);
    assertTrue(json.contains("\"kind\":\"brush-trace\""), json);
  }

  /**
   * The IO must surface non-null measurements (the validation annotation
   * marks them {@code @NotEmpty}). A brush-trace point without intensities
   * is meaningless; this test documents the minimum required key set.
   */
  @Test
  public void payloadPoint_measurementsAlwaysPresent() {
    var point = new SpatialDataPointIO(
      1L,
      0.0,
      0.0,
      0.0,
      Map.of(),
      Map.of(KIND_KEY, BRUSH_TRACE, INTENSITIES_KEY, List.of(1, 2, 3))
    );
    assertNotNull(point.getMeasurements(), "measurements must be set");
    assertTrue(
      point.getMeasurements().containsKey(INTENSITIES_KEY),
      "intensities key is the brush-trace contract"
    );
  }

  /**
   * Container name carries the {@code TPS raw data.N} convention so the
   * promotion UI can show "chunk 18 of 37" without parsing the IO bag.
   * This is documentation as much as a test — locks in the convention used
   * by the importer.
   */
  @Test
  public void container_nameEncodesChunkProvenance() {
    var container = new SpatialDataContainer(99L);
    container.setName("Track_244__Run_30239_/TPS raw data.18");
    var io = SpatialDataContainerIO.fromEntity(container);

    assertTrue(io.getName().contains("TPS raw data"), io.getName());
    assertTrue(
      io.getName().matches(".*Track_\\d+__Run_\\d+_/TPS raw data\\.\\d+$"),
      "container name must encode Track/Run/chunk for UI breadcrumb; got: " + io.getName()
    );
  }
}
