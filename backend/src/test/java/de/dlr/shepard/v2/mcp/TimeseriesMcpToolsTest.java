package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.context.semantic.daos.SemanticRepositoryDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.semantic.services.AnnotatableTimeseriesService;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.repositories.TsChannelResolver;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import io.quarkiverse.mcp.server.McpException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TimeseriesMcpToolsTest {

  static final String CONTAINER_APP_ID = "018f9c5a-7e26-7000-a000-000000000030";
  static final long   CONTAINER_OGM_ID = 77L;

  @Mock TimeseriesService timeseriesService;
  @Mock TsChannelResolver tsChannelResolver;
  @Mock EntityIdResolver entityIdResolver;
  @Mock McpContextBridge contextBridge;
  @Mock AnnotatableTimeseriesService annotatableTimeseriesService;
  @Mock SemanticRepositoryDAO semanticRepositoryDAO;

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
    tools.tsChannelResolver = tsChannelResolver;
    tools.annotatableTimeseriesService = annotatableTimeseriesService;
    tools.semanticRepositoryDAO = semanticRepositoryDAO;
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

    // 1000 ns = 1970-01-01T00:00:00.000001Z; 2000 ns = 1970-01-01T00:00:00.000002Z
    tools.getChannelData(CONTAINER_APP_ID, "vibration", "turbopump", "bearing", "TB1", "rms_g", "1970-01-01T00:00:00.000001Z", "1970-01-01T00:00:00.000002Z", null);

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

  // ── ts_describe (MCP-COV-03) ─────────────────────────────────────────────

  @Test
  void tsDescribeReturnsShepardIdsAndTotalChannels() throws Exception {
    UUID shepardA = UUID.fromString("01930a2b-fe4c-7e3c-9f1d-000000000001");
    UUID shepardB = UUID.fromString("01930a2b-fe4c-7e3c-9f1d-000000000002");
    var entA = new TimeseriesEntity(CONTAINER_OGM_ID, "vibration", "rms_g", "turbopump", "bearing", "TB1", DataPointValueType.Double);
    entA.setShepardId(shepardA);
    var entB = new TimeseriesEntity(CONTAINER_OGM_ID, "thermal", "temp_c", "tcp", "head", "TH1", DataPointValueType.Double);
    entB.setShepardId(shepardB);
    when(tsChannelResolver.listPaged(eq(CONTAINER_OGM_ID), anyInt(), anyInt()))
      .thenReturn(List.of(entA, entB))
      .thenReturn(List.of());

    String json = tools.tsDescribe(CONTAINER_APP_ID);

    var root = new ObjectMapper().readTree(json);
    assertEquals(CONTAINER_APP_ID, root.get("containerAppId").asText());
    assertEquals(2, root.get("totalChannels").asInt());
    var channels = root.get("channels");
    assertTrue(channels.isArray());
    assertEquals(2, channels.size());
    assertEquals(shepardA.toString(), channels.get(0).get("appId").asText());
    assertEquals("vibration", channels.get(0).get("measurement").asText());
    assertEquals("Double", channels.get(0).get("valueType").asText());
    assertTrue(channels.get(0).get("unit").isNull());
    assertTrue(channels.get(0).get("sampleRate").isNull());
    assertEquals(shepardB.toString(), channels.get(1).get("appId").asText());
  }

  @Test
  void tsDescribeReturnsEmptyForEmptyContainer() throws Exception {
    when(tsChannelResolver.listPaged(eq(CONTAINER_OGM_ID), anyInt(), anyInt())).thenReturn(List.of());
    String json = tools.tsDescribe(CONTAINER_APP_ID);
    var root = new ObjectMapper().readTree(json);
    assertEquals(0, root.get("totalChannels").asInt());
    assertEquals(0, root.get("channels").size());
  }

  @Test
  void tsDescribeRejectsWrongContainerType() {
    when(entityIdResolver.resolveWithLabels(CONTAINER_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(CONTAINER_OGM_ID, List.of("FileContainer")));
    McpException ex = assertThrows(McpException.class, () -> tools.tsDescribe(CONTAINER_APP_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("TimeseriesContainer"));
  }

  // ── ts_query_multi (MCP-COV-03) ───────────────────────────────────────────

  @Test
  void tsQueryMultiReturnsRawPointsForResolvedChannels() throws Exception {
    UUID shepardA = UUID.fromString("01930a2b-fe4c-7e3c-9f1d-000000000001");
    var entA = new TimeseriesEntity(CONTAINER_OGM_ID, "vibration", "rms_g", "turbopump", "bearing", "TB1", DataPointValueType.Double);
    entA.setShepardId(shepardA);
    when(tsChannelResolver.bulkFindByShepardIds(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(entA));
    when(timeseriesService.getManyDataPointsByEntities(eq(CONTAINER_OGM_ID), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
      .thenReturn(List.of(new TimeseriesWithDataPoints(
        new Timeseries(entA),
        List.of(new TimeseriesDataPoint(1L, 10.0), new TimeseriesDataPoint(2L, 11.0))
      )));

    String json = tools.tsQueryMulti(CONTAINER_APP_ID, List.of(shepardA.toString()), "1970-01-01T00:00:00Z", "1970-01-01T00:00:01Z", null);

    var root = new ObjectMapper().readTree(json);
    assertEquals(1, root.get("requested").asInt());
    assertEquals(1, root.get("resolved").asInt());
    var channels = root.get("channels");
    assertEquals(1, channels.size());
    var ch = channels.get(0);
    assertEquals("vibration", ch.get("measurement").asText());
    assertEquals(2, ch.get("rawCount").asInt());
    assertEquals(2, ch.get("returnedCount").asInt());
    assertEquals(false, ch.get("downsampled").asBoolean());
    assertEquals("none", ch.get("algorithm").asText());
    assertEquals(2, ch.get("points").size());
  }

  @Test
  void tsQueryMultiDownsamplesPerChannelWhenCapped() throws Exception {
    UUID shepardA = UUID.fromString("01930a2b-fe4c-7e3c-9f1d-000000000001");
    var entA = new TimeseriesEntity(CONTAINER_OGM_ID, "m", "d", "l", "s", "f", DataPointValueType.Double);
    entA.setShepardId(shepardA);
    when(tsChannelResolver.bulkFindByShepardIds(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(entA));
    List<TimeseriesDataPoint> many = new ArrayList<>();
    for (int i = 0; i < 1000; i++) many.add(new TimeseriesDataPoint(i, (double) i));
    when(timeseriesService.getManyDataPointsByEntities(eq(CONTAINER_OGM_ID), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
      .thenReturn(List.of(new TimeseriesWithDataPoints(new Timeseries(entA), many)));

    String json = tools.tsQueryMulti(CONTAINER_APP_ID, List.of(shepardA.toString()), "1970-01-01T00:00:00Z", "1970-01-01T00:00:01Z", 50);

    var root = new ObjectMapper().readTree(json);
    var ch = root.get("channels").get(0);
    assertEquals(1000, ch.get("rawCount").asInt());
    assertEquals(50, ch.get("returnedCount").asInt());
    assertEquals(true, ch.get("downsampled").asBoolean());
    assertEquals("LTTB", ch.get("algorithm").asText());
  }

  @Test
  void tsQueryMultiRejectsEmptyChannelList() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.tsQueryMulti(CONTAINER_APP_ID, List.of(), "1970-01-01T00:00:00Z", "1970-01-01T00:00:00.000000001Z", null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void tsQueryMultiRejectsInvalidUuid() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.tsQueryMulti(CONTAINER_APP_ID, List.of("not-a-uuid"), "1970-01-01T00:00:00Z", "1970-01-01T00:00:00.000000001Z", null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().toLowerCase().contains("uuid"));
  }

  @Test
  void tsQueryMultiRejectsTooManyChannels() {
    List<String> tooMany = new ArrayList<>();
    for (int i = 0; i < TimeseriesMcpTools.TS_QUERY_MULTI_MAX_CHANNELS + 1; i++) {
      tooMany.add(UUID.randomUUID().toString());
    }
    McpException ex = assertThrows(McpException.class,
      () -> tools.tsQueryMulti(CONTAINER_APP_ID, tooMany, "1970-01-01T00:00:00Z", "1970-01-01T00:00:00.000000001Z", null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void tsQueryMultiRejectsMissingWindow() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.tsQueryMulti(CONTAINER_APP_ID, List.of(UUID.randomUUID().toString()), null, "1970-01-01T00:00:00.000000001Z", null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void tsQueryMultiSilentlySkipsUnknownShepardIds() throws Exception {
    UUID shepardA = UUID.fromString("01930a2b-fe4c-7e3c-9f1d-000000000001");
    UUID shepardB = UUID.fromString("01930a2b-fe4c-7e3c-9f1d-000000000099"); // unknown
    var entA = new TimeseriesEntity(CONTAINER_OGM_ID, "m", "d", "l", "s", "f", DataPointValueType.Double);
    entA.setShepardId(shepardA);
    when(tsChannelResolver.bulkFindByShepardIds(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(entA));
    when(timeseriesService.getManyDataPointsByEntities(eq(CONTAINER_OGM_ID), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
      .thenReturn(List.of(new TimeseriesWithDataPoints(new Timeseries(entA), List.of())));

    String json = tools.tsQueryMulti(CONTAINER_APP_ID, List.of(shepardA.toString(), shepardB.toString()), "1970-01-01T00:00:00Z", "1970-01-01T00:00:00.000000001Z", null);

    var root = new ObjectMapper().readTree(json);
    assertEquals(2, root.get("requested").asInt());
    assertEquals(1, root.get("resolved").asInt());
  }

  // ── create_channel_annotation (APISIMP-MCP-VOCAB-NUMERIC-ARGS) ──────────

  static final String CHANNEL_SHEPARD_ID  = "01930a2b-fe4c-7e3c-9f1d-000000000010";
  static final String PROP_VOCAB_APP_ID   = "01930a2b-fe4c-7e3c-9f1d-000000000020";
  static final String VAL_VOCAB_APP_ID    = "01930a2b-fe4c-7e3c-9f1d-000000000021";

  @Test
  void createChannelAnnotation_happyPath_resolvesAppIdAndCreates() throws Exception {
    var propRepo = new SemanticRepository(42L);
    var valRepo  = new SemanticRepository(99L);
    when(semanticRepositoryDAO.findByAppId(PROP_VOCAB_APP_ID)).thenReturn(propRepo);
    when(semanticRepositoryDAO.findByAppId(VAL_VOCAB_APP_ID)).thenReturn(valRepo);

    var ann = new SemanticAnnotation();
    ann.setPropertyIRI("http://example.org/prop");
    ann.setValueIRI("http://example.org/val");
    when(annotatableTimeseriesService.createAnnotationForChannel(
            eq(CONTAINER_OGM_ID), eq(CHANNEL_SHEPARD_ID), org.mockito.ArgumentMatchers.any()))
        .thenReturn(ann);

    String json = tools.createChannelAnnotation(
        CONTAINER_APP_ID, CHANNEL_SHEPARD_ID,
        "http://example.org/prop", PROP_VOCAB_APP_ID,
        "http://example.org/val",  VAL_VOCAB_APP_ID);

    assertNotNull(json);
    var root = new ObjectMapper().readTree(json);
    assertEquals("http://example.org/prop", root.get("propertyIRI").asText());
    assertEquals("http://example.org/val",  root.get("valueIRI").asText());
  }

  @Test
  void createChannelAnnotation_unknownPropVocabAppId_throwsInvalidParams() {
    when(semanticRepositoryDAO.findByAppId(PROP_VOCAB_APP_ID)).thenReturn(null);

    McpException ex = assertThrows(McpException.class, () ->
        tools.createChannelAnnotation(
            CONTAINER_APP_ID, CHANNEL_SHEPARD_ID,
            "http://example.org/prop", PROP_VOCAB_APP_ID,
            "http://example.org/val",  VAL_VOCAB_APP_ID));

    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains(PROP_VOCAB_APP_ID));
  }

  @Test
  void createChannelAnnotation_unknownValVocabAppId_throwsInvalidParams() {
    var propRepo = new SemanticRepository(42L);
    when(semanticRepositoryDAO.findByAppId(PROP_VOCAB_APP_ID)).thenReturn(propRepo);
    when(semanticRepositoryDAO.findByAppId(VAL_VOCAB_APP_ID)).thenReturn(null);

    McpException ex = assertThrows(McpException.class, () ->
        tools.createChannelAnnotation(
            CONTAINER_APP_ID, CHANNEL_SHEPARD_ID,
            "http://example.org/prop", PROP_VOCAB_APP_ID,
            "http://example.org/val",  VAL_VOCAB_APP_ID));

    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains(VAL_VOCAB_APP_ID));
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
