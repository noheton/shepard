package de.dlr.shepard.v2.references.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.context.references.timeseriesreference.daos.ReferencedTimeseriesNodeEntityDAO;
import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import de.dlr.shepard.v2.timeseries.daos.TimeseriesAnnotationDAO;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * REF-EDIT-1 — unit tests for {@link TimeseriesReferenceKindHandler#patch},
 * covering the channel-flip, bounds-update, null-bounds, and 404 paths.
 */
class TimeseriesReferenceKindHandlerTest {

  private static final String REF_APP_ID = "ts-ref-app-42";

  @Mock
  TimeseriesReferenceDAO timeseriesReferenceDAO;

  @Mock
  TimeseriesReferenceService timeseriesReferenceService;

  @Mock
  ReferencedTimeseriesNodeEntityDAO tsNodeEntityDAO;

  @Mock
  DataObjectDAO dataObjectDAO;

  @Mock
  TimeseriesAnnotationDAO tsAnnotationDAO;

  TimeseriesReferenceKindHandler handler;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    handler = new TimeseriesReferenceKindHandler();
    handler.timeseriesReferenceDAO = timeseriesReferenceDAO;
    handler.timeseriesReferenceService = timeseriesReferenceService;
    handler.tsNodeEntityDAO = tsNodeEntityDAO;
    handler.dataObjectDAO = dataObjectDAO;
    handler.tsAnnotationDAO = tsAnnotationDAO;
    handler.objectMapper = new ObjectMapper();
  }

  /** Build a minimal TimeseriesReference with one existing channel. */
  private TimeseriesReference makeRef() {
    var coll = new Collection(1L);
    coll.setShepardId(1L);
    var parent = new DataObject(10L);
    parent.setShepardId(10L);
    parent.setCollection(coll);

    var ref = new TimeseriesReference(42L);
    ref.setAppId(REF_APP_ID);
    ref.setShepardId(42L);
    ref.setName("original-name");
    ref.setStart(1_000_000_000L);
    ref.setEnd(2_000_000_000L);
    ref.setDataObject(parent);

    var existingChannel = new ReferencedTimeseriesNodeEntity();
    existingChannel.setMeasurement("m1");
    existingChannel.setDevice("d1");
    existingChannel.setLocation("l1");
    existingChannel.setSymbolicName("s1");
    existingChannel.setField("f1");
    ref.addTimeseries(existingChannel);

    return ref;
  }

  // ─── kind / owns ────────────────────────────────────────────────────────────

  @Test
  void kind_returnsTimeseries() {
    assertEquals("timeseries", handler.kind());
  }

  @Test
  void owns_timeseriesReferenceReturnsTrue() {
    assertTrue(handler.owns(makeRef()));
  }

  @Test
  void owns_otherKindReturnsFalse() {
    assertFalse(handler.owns(new URIReference(99L)));
  }

  // ─── patch: 404 ─────────────────────────────────────────────────────────────

  @Test
  void patch_notFound_throwsNotFoundException() {
    when(timeseriesReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(null);
    assertThrows(NotFoundException.class,
        () -> handler.patch(REF_APP_ID, Map.of("name", "irrelevant")));
  }

  // ─── patch: name update ─────────────────────────────────────────────────────

  @Test
  void patch_nameUpdate_appliesNewName() {
    var ref = makeRef();
    when(timeseriesReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);
    when(timeseriesReferenceService.updateTimeReference(any(), any())).thenAnswer(inv -> inv.getArgument(0));

    handler.patch(REF_APP_ID, Map.of("name", "  new-name  "));

    assertEquals("new-name", ref.getName());
  }

  // ─── toIO: ISO 8601 start/end (APISIMP-TSREF-TIMEWINDOW-NANOS) ─────────────

  @Test
  void toIO_convertsStartAndEndToIso8601() {
    // ref.start = 1_000_000_000 ns = 1 s since epoch → 1970-01-01T00:00:01Z
    // ref.end   = 2_000_000_000 ns = 2 s since epoch → 1970-01-01T00:00:02Z
    var ref = makeRef();
    var io = handler.toIO(ref);
    assertEquals("1970-01-01T00:00:01Z", io.getPayload().get("start"));
    assertEquals("1970-01-01T00:00:02Z", io.getPayload().get("end"));
  }

  // ─── patch: bounds update ───────────────────────────────────────────────────

  @Test
  void patch_boundsUpdate_appliesStartAndEnd() {
    var ref = makeRef();
    when(timeseriesReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);
    when(timeseriesReferenceService.updateTimeReference(any(), any())).thenAnswer(inv -> inv.getArgument(0));

    handler.patch(REF_APP_ID, Map.of("start", 5_000_000_000L, "end", 9_000_000_000L));

    assertEquals(5_000_000_000L, ref.getStart());
    assertEquals(9_000_000_000L, ref.getEnd());
  }

  @Test
  void patch_boundsUpdateIso8601_appliesStartAndEnd() {
    var ref = makeRef();
    when(timeseriesReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);
    when(timeseriesReferenceService.updateTimeReference(any(), any())).thenAnswer(inv -> inv.getArgument(0));

    // 1970-01-01T00:00:05Z = 5_000_000_000 ns; 1970-01-01T00:00:09Z = 9_000_000_000 ns
    handler.patch(REF_APP_ID, Map.of("start", "1970-01-01T00:00:05Z", "end", "1970-01-01T00:00:09Z"));

    assertEquals(5_000_000_000L, ref.getStart());
    assertEquals(9_000_000_000L, ref.getEnd());
  }

  @Test
  void patch_boundsUpdateInvalidString_throwsBadRequest() {
    var ref = makeRef();
    when(timeseriesReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);

    assertThrows(BadRequestException.class,
        () -> handler.patch(REF_APP_ID, Map.of("start", "not-a-date")));
  }

  // ─── patch: absent bounds ───────────────────────────────────────────────────

  @Test
  void patch_absentBoundsKeys_leavesStartAndEndUnchanged() {
    var ref = makeRef();
    long originalStart = ref.getStart();
    long originalEnd   = ref.getEnd();
    when(timeseriesReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);
    when(timeseriesReferenceService.updateTimeReference(any(), any())).thenAnswer(inv -> inv.getArgument(0));

    // Only name is in the patch; start/end keys are absent.
    handler.patch(REF_APP_ID, Map.of("name", "keep-bounds"));

    assertEquals(originalStart, ref.getStart());
    assertEquals(originalEnd,   ref.getEnd());
  }

  // ─── create: ISO 8601 start/end (APISIMP-TSREF-TIMEWINDOW-NANOS) ───────────

  @Test
  void create_acceptsIso8601StartAndEnd() {
    var coll = new Collection(1L);
    coll.setShepardId(1L);
    var parent = new DataObject(10L);
    parent.setShepardId(10L);
    parent.setCollection(coll);
    when(dataObjectDAO.findByAppId("do-app-1")).thenReturn(parent);
    when(timeseriesReferenceService.createReference(anyLong(), anyLong(), any())).thenReturn(makeRef());

    var body = new java.util.HashMap<String, Object>();
    body.put("start", "1970-01-01T00:00:01Z");
    body.put("end",   "1970-01-01T00:00:02Z");
    body.put("timeseriesContainerId", 5L);
    body.put("timeseries", List.of(Map.of(
        "measurement", "m", "device", "d", "location", "l", "symbolicName", "s", "field", "f"
    )));

    // Should not throw — ISO 8601 strings are accepted and normalised to nanosecond longs.
    handler.create("do-app-1", body);
  }

  // ─── patch: channel flip ────────────────────────────────────────────────────

  @Test
  void patch_channelFlip_replacesChannelList() {
    var ref = makeRef();
    when(timeseriesReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);
    when(timeseriesReferenceService.updateTimeReference(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    // Simulate no pre-existing node found — new entity will be created.
    when(tsNodeEntityDAO.find(any(), any(), any(), any(), any())).thenReturn(null);

    var newChannel = Map.of(
        "measurement", "pressure",
        "device",      "sensor-2",
        "location",    "LOC-B",
        "symbolicName", "P2",
        "field",       "value"
    );
    handler.patch(REF_APP_ID, Map.of("timeseries", List.of(newChannel)));

    assertEquals(1, ref.getReferencedTimeseriesList().size());
    var added = ref.getReferencedTimeseriesList().get(0);
    assertEquals("pressure", added.getMeasurement());
    assertEquals("sensor-2", added.getDevice());
    assertEquals("P2",       added.getSymbolicName());
  }

  // ─── patch: reuse existing node ─────────────────────────────────────────────

  @Test
  void patch_channelFlip_reusesExistingNodeEntity() {
    var ref = makeRef();
    when(timeseriesReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);
    when(timeseriesReferenceService.updateTimeReference(any(), any())).thenAnswer(inv -> inv.getArgument(0));

    var existingNode = new ReferencedTimeseriesNodeEntity();
    existingNode.setMeasurement("vibration");
    existingNode.setDevice("acc-1");
    existingNode.setLocation("LOC-C");
    existingNode.setSymbolicName("V1");
    existingNode.setField("rms");
    when(tsNodeEntityDAO.find("vibration", "acc-1", "LOC-C", "V1", "rms")).thenReturn(existingNode);

    var newChannel = Map.of(
        "measurement", "vibration",
        "device",      "acc-1",
        "location",    "LOC-C",
        "symbolicName", "V1",
        "field",       "rms"
    );
    handler.patch(REF_APP_ID, Map.of("timeseries", List.of(newChannel)));

    assertEquals(1, ref.getReferencedTimeseriesList().size());
    // Verify the same pre-existing node was attached, not a new one.
    assertTrue(ref.getReferencedTimeseriesList().contains(existingNode));
  }

  // ─── patch: invalid channel (missing field) ─────────────────────────────────

  @Test
  void patch_channelMissingRequiredField_throwsBadRequest() {
    var ref = makeRef();
    when(timeseriesReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);

    // "field" key is absent → TimeseriesValidator should reject.
    var badChannel = new java.util.HashMap<String, Object>();
    badChannel.put("measurement", "m");
    badChannel.put("device",      "d");
    badChannel.put("location",    "l");
    badChannel.put("symbolicName", "s");
    // "field" intentionally omitted → TimeseriesValidator throws InvalidBodyException (a WebApplicationException).
    assertThrows(WebApplicationException.class,
        () -> handler.patch(REF_APP_ID, Map.of("timeseries", List.of(badChannel))));
  }

  // ─── annotation map: ISO 8601 (APISIMP-TSREF-ANNOT-MAP-NS-TO-ISO) ───────────

  private TimeseriesAnnotation makeAnnotation(long startNs, Long endNs) {
    var a = new TimeseriesAnnotation();
    a.setAppId("ann-app-id");
    a.setStartNs(startNs);
    a.setEndNs(endNs);
    a.setLabel("spike");
    return a;
  }

  @Test
  void createAnnotation_acceptsIso8601Start_emitsIso8601Response() {
    var body = new java.util.HashMap<String, Object>();
    body.put("start", "1970-01-01T00:00:01Z");  // = 1_000_000_000 ns
    body.put("label", "spike");
    when(tsAnnotationDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = handler.createAnnotation(REF_APP_ID, body);

    assertEquals("1970-01-01T00:00:01Z", result.get("start"));
    assertNull(result.get("end"));
  }

  @Test
  void createAnnotation_acceptsLegacyStartNs_emitsIso8601Response() {
    var body = new java.util.HashMap<String, Object>();
    body.put("startNs", 2_000_000_000L);  // = 1970-01-01T00:00:02Z
    body.put("label", "ramp");
    when(tsAnnotationDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = handler.createAnnotation(REF_APP_ID, body);

    assertEquals("1970-01-01T00:00:02Z", result.get("start"));
  }

  @Test
  void createAnnotation_missingStart_throwsBadRequest() {
    assertThrows(BadRequestException.class,
        () -> handler.createAnnotation(REF_APP_ID, Map.of("label", "no-start")));
  }

  @Test
  void patchAnnotation_acceptsIso8601StartAndEnd() {
    var a = makeAnnotation(1_000_000_000L, null);
    when(tsAnnotationDAO.findByAppId("ann-app-id")).thenReturn(a);
    when(tsAnnotationDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    var patch = new java.util.HashMap<String, Object>();
    patch.put("start", "1970-01-01T00:00:05Z"); // 5_000_000_000 ns
    patch.put("end",   "1970-01-01T00:00:09Z"); // 9_000_000_000 ns
    var result = handler.patchAnnotation(REF_APP_ID, "ann-app-id", patch);

    assertEquals("1970-01-01T00:00:05Z", result.get("start"));
    assertEquals("1970-01-01T00:00:09Z", result.get("end"));
  }
}
