package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import io.quarkiverse.mcp.server.McpException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TimeseriesMcpToolsTest {

  static final String CONTAINER_APP_ID = "018f9c5a-7e26-7000-a000-000000000030";
  static final long   CONTAINER_OGM_ID = 77L;

  @Mock TimeseriesService timeseriesService;
  @Mock EntityIdResolver entityIdResolver;
  @Mock McpContextBridge contextBridge;

  TimeseriesMcpTools tools;
  McpToolSupport support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.entityIdResolver = entityIdResolver;
    support.objectMapper = new ObjectMapper();
    tools = new TimeseriesMcpTools();
    tools.timeseriesService = timeseriesService;
    tools.contextBridge = contextBridge;
    tools.support = support;
    // Default: resolver returns a TimeseriesContainer-labeled node. Tests that
    // care about the wrong-label path override this.
    when(entityIdResolver.resolveWithLabels(CONTAINER_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(CONTAINER_OGM_ID, List.of("TimeseriesContainer")));
  }

  @Test
  void listChannelsThrowsInvalidParamsWhenContainerUnknown() {
    when(entityIdResolver.resolveWithLabels(CONTAINER_APP_ID)).thenThrow(new NotFoundException());

    McpException ex = assertThrows(McpException.class, () -> tools.listChannels(CONTAINER_APP_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains(CONTAINER_APP_ID));
  }

  @Test
  void listChannelsRejectsWrongContainerTypeAsInvalidParams() {
    // Agent paste the StructuredDataContainer appId here by mistake.
    when(entityIdResolver.resolveWithLabels(CONTAINER_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(CONTAINER_OGM_ID, List.of("StructuredDataContainer")));

    McpException ex = assertThrows(McpException.class, () -> tools.listChannels(CONTAINER_APP_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("TimeseriesContainer"));
    assertTrue(ex.getMessage().contains("StructuredDataContainer"));
    assertTrue(ex.getMessage().contains("containerAppId"));
  }

  @Test
  void listChannelsRejectsBlankContainerAppId() {
    McpException ex = assertThrows(McpException.class, () -> tools.listChannels(" "));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void listChannelsReturnsJsonArrayWithFiveTuple() throws Exception {
    var entity = new TimeseriesEntity(CONTAINER_OGM_ID, "vibration", "rms_g", "turbopump", "turbopump_bearing", "TB1", DataPointValueType.Double);
    // resolver already stubbed for the happy path in setUp()
    when(timeseriesService.getTimeseriesAvailable(CONTAINER_OGM_ID)).thenReturn(List.of(entity));

    String json = tools.listChannels(CONTAINER_APP_ID);

    assertNotNull(json);
    var root = new ObjectMapper().readTree(json);
    assertTrue(root.isArray());
    assertEquals(1, root.size());
    var row = root.get(0);
    assertEquals("vibration", row.get("measurement").asText());
    assertEquals("turbopump", row.get("device").asText());
    assertEquals("rms_g", row.get("field").asText());
    assertEquals("turbopump_bearing", row.get("location").asText());
    assertEquals("TB1", row.get("symbolicName").asText());
  }

  @Test
  void listChannelsReturnsEmptyArrayForEmptyContainer() throws Exception {
    // resolver already stubbed for the happy path in setUp()
    when(timeseriesService.getTimeseriesAvailable(CONTAINER_OGM_ID)).thenReturn(List.of());

    String json = tools.listChannels(CONTAINER_APP_ID);

    var root = new ObjectMapper().readTree(json);
    assertTrue(root.isArray());
    assertEquals(0, root.size());
  }

  // ── get_channel_data ──────────────────────────────────────────────────────

  @Test
  void getChannelDataReturnsRawPointsWhenUnderCap() throws Exception {
    // resolver already stubbed for the happy path in setUp()
    when(timeseriesService.getDataPointsByTimeseries(anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
      .thenReturn(List.of(
        new TimeseriesDataPoint(1L, 10.0),
        new TimeseriesDataPoint(2L, 11.0),
        new TimeseriesDataPoint(3L, 12.0)
      ));

    String json = tools.getChannelData(CONTAINER_APP_ID, "vibration", "turbopump", "bearing", "TB1", "rms_g", null, null, 100);

    var root = new ObjectMapper().readTree(json);
    assertEquals(3, root.get("raw_count").asInt());
    assertEquals(3, root.get("returned_count").asInt());
    assertEquals(false, root.get("downsampled").asBoolean());
    assertEquals("none", root.get("algorithm").asText());
    assertEquals(3, root.get("points").size());
    assertEquals(10.0, root.get("points").get(0).get("value").asDouble());
  }

  @Test
  void getChannelDataLttbDownsamplesWhenOverCap() throws Exception {
    // resolver already stubbed for the happy path in setUp()
    List<TimeseriesDataPoint> many = new ArrayList<>();
    for (int i = 0; i < 1000; i++) many.add(new TimeseriesDataPoint(i, Math.sin(i / 50.0) * 100));
    when(timeseriesService.getDataPointsByTimeseries(anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
      .thenReturn(many);

    String json = tools.getChannelData(CONTAINER_APP_ID, "m", "d", "l", "s", "f", null, null, 50);

    var root = new ObjectMapper().readTree(json);
    assertEquals(1000, root.get("raw_count").asInt());
    assertEquals(50, root.get("returned_count").asInt());
    assertEquals(true, root.get("downsampled").asBoolean());
    assertEquals("LTTB", root.get("algorithm").asText());
    // LTTB always preserves first and last sample.
    assertEquals(0L, root.get("points").get(0).get("timestamp").asLong());
    assertEquals(999L, root.get("points").get(49).get("timestamp").asLong());
  }

  @Test
  void getChannelDataPassesQueryWindowToService() throws Exception {
    // resolver already stubbed for the happy path in setUp()
    when(timeseriesService.getDataPointsByTimeseries(anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
      .thenReturn(List.of());
    ArgumentCaptor<TimeseriesDataPointsQueryParams> paramsCap = ArgumentCaptor.forClass(TimeseriesDataPointsQueryParams.class);
    ArgumentCaptor<Timeseries> tsCap = ArgumentCaptor.forClass(Timeseries.class);

    tools.getChannelData(CONTAINER_APP_ID, "vibration", "turbopump", "bearing", "TB1", "rms_g", 1000L, 2000L, null);

    org.mockito.Mockito.verify(timeseriesService)
      .getDataPointsByTimeseries(org.mockito.ArgumentMatchers.eq(CONTAINER_OGM_ID), tsCap.capture(), paramsCap.capture());
    assertEquals(1000L, paramsCap.getValue().getStartTime());
    assertEquals(2000L, paramsCap.getValue().getEndTime());
    assertEquals("vibration", tsCap.getValue().getMeasurement());
    assertEquals("rms_g", tsCap.getValue().getField());
  }

  @Test
  void getChannelDataMaxPointsIsCapped() throws Exception {
    // resolver already stubbed for the happy path in setUp()
    List<TimeseriesDataPoint> many = new ArrayList<>();
    for (int i = 0; i < 20_000; i++) many.add(new TimeseriesDataPoint(i, (double) i));
    when(timeseriesService.getDataPointsByTimeseries(anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
      .thenReturn(many);

    // Caller asks for 100000 — cap clamps to 5000.
    String json = tools.getChannelData(CONTAINER_APP_ID, "m", "d", "l", "s", "f", null, null, 100_000);

    var root = new ObjectMapper().readTree(json);
    assertEquals(20_000, root.get("raw_count").asInt());
    assertEquals(5000, root.get("returned_count").asInt());
    assertEquals(true, root.get("downsampled").asBoolean());
  }

  @Test
  void getChannelDataAddsNoteWhenChannelIsEmpty() throws Exception {
    // resolver already stubbed for the happy path in setUp()
    when(timeseriesService.getDataPointsByTimeseries(anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
      .thenReturn(List.of());

    String json = tools.getChannelData(CONTAINER_APP_ID, "vibration", "turbopump", "bearing", "TB1", "rms_g", null, null, null);

    var root = new ObjectMapper().readTree(json);
    assertEquals(0, root.get("raw_count").asInt());
    assertEquals(0, root.get("returned_count").asInt());
    assertTrue(root.has("note"), "empty-window response should explain the empty result");
    assertTrue(root.get("note").asText().toLowerCase().contains("no samples"));
  }

  @Test
  void getChannelDataRejectsBlankChannelFields() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.getChannelData(CONTAINER_APP_ID, "", "d", "l", "s", "f", null, null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("measurement") || ex.getMessage().contains("list_channels"));
  }

  // ── LTTB unit tests ──────────────────────────────────────────────────────

  @Test
  void lttbPreservesEndpoints() {
    List<TimeseriesDataPoint> input = new ArrayList<>();
    for (int i = 0; i < 100; i++) input.add(new TimeseriesDataPoint(i, (double) i));
    List<TimeseriesDataPoint> out = TimeseriesMcpTools.lttb(input, 10);
    assertEquals(10, out.size());
    assertEquals(0L, out.get(0).getTimestamp());
    assertEquals(99L, out.get(9).getTimestamp());
  }

  @Test
  void lttbReturnsInputWhenSmallEnough() {
    List<TimeseriesDataPoint> input = List.of(
      new TimeseriesDataPoint(1L, 1.0),
      new TimeseriesDataPoint(2L, 2.0)
    );
    List<TimeseriesDataPoint> out = TimeseriesMcpTools.lttb(input, 10);
    assertEquals(2, out.size());
  }
}
