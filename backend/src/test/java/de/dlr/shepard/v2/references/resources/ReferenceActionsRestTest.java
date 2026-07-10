package de.dlr.shepard.v2.references.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.AnomalyDetectionService;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.v2.references.services.ReferencesV2Service;
import de.dlr.shepard.v2.references.services.ReferencesV2Service.ResolvedReference;
import de.dlr.shepard.v2.timeseries.io.AnomalyDetectRequestIO;
import de.dlr.shepard.v2.timeseries.io.AnomalyDetectResultIO;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for ReferenceActionsRest — covers action routing, auth gate,
 * parameter validation, series selection, and kind-discriminated 422.
 */
class ReferenceActionsRestTest {

  static final String REF_APP_ID = "ref-appid-1";
  static final String DO_APP_ID  = "do-appid-1";
  static final long   DO_OGM_ID  = 99L;
  static final String CALLER     = "alice";
  static final String ACTION     = "detect-anomalies";

  @Mock TimeseriesReferenceDAO timeseriesReferenceDAO;
  @Mock PermissionsService permissionsService;
  @Mock AnomalyDetectionService anomalyDetectionService;
  @Mock ReferencesV2Service referencesV2Service;
  @Mock SecurityContext sc;
  @Mock Principal principal;

  ReferenceActionsRest resource;
  TimeseriesReference ref;
  DataObject dataObject;
  TimeseriesContainer container;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new ReferenceActionsRest();
    resource.timeseriesReferenceDAO = timeseriesReferenceDAO;
    resource.permissionsService = permissionsService;
    resource.anomalyDetectionService = anomalyDetectionService;
    resource.referencesV2Service = referencesV2Service;

    dataObject = new DataObject();
    dataObject.setId(DO_OGM_ID);
    dataObject.setAppId(DO_APP_ID);

    container = new TimeseriesContainer();
    container.setDeleted(false);

    ref = new TimeseriesReference();
    ref.setAppId(REF_APP_ID);
    ref.setDataObject(dataObject);
    ref.setTimeseriesContainer(container);
    ref.setReferencedTimeseriesList(List.of(makeSeries("hotfire", "vib_fuel_pump")));

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(timeseriesReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), any(), eq(CALLER)))
      .thenReturn(true);
    when(anomalyDetectionService.detect(any(), any(), any()))
      .thenReturn(new AnomalyDetectResultIO(List.of(), 11, 3.0, 0, 0));
    when(referencesV2Service.resolveByAppId(anyString())).thenReturn(Optional.empty());
  }

  // ── action routing ────────────────────────────────────────────────────────

  @Test
  void perform_returns422WhenActionParamMissing() {
    assertThat(resource.perform(REF_APP_ID, null, new AnomalyDetectRequestIO(), sc).getStatus())
        .isEqualTo(422);
  }

  @Test
  void perform_returns422WhenActionParamBlank() {
    assertThat(resource.perform(REF_APP_ID, "  ", new AnomalyDetectRequestIO(), sc).getStatus())
        .isEqualTo(422);
  }

  @Test
  void perform_returns422WhenActionUnknown() {
    assertThat(resource.perform(REF_APP_ID, "explode", new AnomalyDetectRequestIO(), sc).getStatus())
        .isEqualTo(422);
  }

  @Test
  void perform_returns422WhenKnownActionButNonTimeseriesKind() {
    when(timeseriesReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(null);
    when(referencesV2Service.resolveByAppId(REF_APP_ID)).thenReturn(Optional.of(
        new ResolvedReference(null, null)
    ));
    assertThat(resource.perform(REF_APP_ID, ACTION, new AnomalyDetectRequestIO(), sc).getStatus())
        .isEqualTo(422);
  }

  // ── auth & not-found ──────────────────────────────────────────────────────

  @Test
  void perform_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertThat(resource.perform(REF_APP_ID, ACTION, new AnomalyDetectRequestIO(), sc).getStatus())
        .isEqualTo(401);
  }

  @Test
  void perform_returns404WhenRefMissing() {
    when(timeseriesReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(null);
    assertThat(resource.perform(REF_APP_ID, ACTION, new AnomalyDetectRequestIO(), sc).getStatus())
        .isEqualTo(404);
  }

  @Test
  void perform_returns404WhenDataObjectMissing() {
    ref.setDataObject(null);
    assertThat(resource.perform(REF_APP_ID, ACTION, new AnomalyDetectRequestIO(), sc).getStatus())
        .isEqualTo(404);
  }

  @Test
  void perform_returns403WhenNoReadPermission() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    assertThat(resource.perform(REF_APP_ID, ACTION, new AnomalyDetectRequestIO(), sc).getStatus())
        .isEqualTo(403);
  }

  /**
   * Regression guard: the old code called isAccessTypeAllowedForUser(ogmId, ..., 0L).
   * DataObjects have no own :Permissions node so that call always returned false (403).
   * After the fix the gate uses isAccessAllowedForDataObjectAppId(appId, ...) which
   * walks up to the parent Collection — this test verifies the corrected call site.
   */
  @Test
  void perform_usesDataObjectAppIdForPermissionCheck() {
    resource.perform(REF_APP_ID, ACTION, new AnomalyDetectRequestIO(), sc);
    verify(permissionsService).isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER));
    verify(permissionsService, never()).isAccessTypeAllowedForUser(anyLong(), any(AccessType.class), anyString(), anyLong());
  }

  // ── parameter validation ──────────────────────────────────────────────────

  @Test
  void perform_returns400WhenWindowTooSmall() {
    var body = new AnomalyDetectRequestIO();
    body.setWindow(2);
    assertThat(resource.perform(REF_APP_ID, ACTION, body, sc).getStatus()).isEqualTo(400);
  }

  @Test
  void perform_returns400WhenKNonPositive() {
    var body = new AnomalyDetectRequestIO();
    body.setK(0.0);
    assertThat(resource.perform(REF_APP_ID, ACTION, body, sc).getStatus()).isEqualTo(400);
  }

  // ── container + series selection ──────────────────────────────────────────

  @Test
  void perform_returns400WhenContainerIsDeleted() {
    container.setDeleted(true);
    assertThat(resource.perform(REF_APP_ID, ACTION, new AnomalyDetectRequestIO(), sc).getStatus())
        .isEqualTo(400);
  }

  @Test
  void perform_returns400WhenContainerIsNull() {
    ref.setTimeseriesContainer(null);
    assertThat(resource.perform(REF_APP_ID, ACTION, new AnomalyDetectRequestIO(), sc).getStatus())
        .isEqualTo(400);
  }

  @Test
  void perform_returns400WhenMultipleSeriesAndNoFilter() {
    ref.setReferencedTimeseriesList(List.of(
      makeSeries("hotfire", "vib_fuel_pump"),
      makeSeries("hotfire", "vib_lox_pump")
    ));
    assertThat(resource.perform(REF_APP_ID, ACTION, new AnomalyDetectRequestIO(), sc).getStatus())
        .isEqualTo(400);
  }

  @Test
  void perform_returns200WithAutoSelectWhenExactlyOneSeries() {
    var r = resource.perform(REF_APP_ID, ACTION, new AnomalyDetectRequestIO(), sc);
    assertThat(r.getStatus()).isEqualTo(200);
    verify(anomalyDetectionService).detect(any(), any(), any());
  }

  @Test
  void perform_returns200WhenFilterMatchesOneSeries() {
    ref.setReferencedTimeseriesList(List.of(
      makeSeries("hotfire", "vib_fuel_pump"),
      makeSeries("hotfire", "vib_lox_pump")
    ));
    var body = new AnomalyDetectRequestIO();
    body.setSymbolicName("vib_fuel_pump");
    assertThat(resource.perform(REF_APP_ID, ACTION, body, sc).getStatus()).isEqualTo(200);
  }

  @Test
  void perform_returns400WhenFilterMatchesNoSeries() {
    var body = new AnomalyDetectRequestIO();
    body.setSymbolicName("does_not_exist");
    assertThat(resource.perform(REF_APP_ID, ACTION, body, sc).getStatus()).isEqualTo(400);
  }

  // ── channelAppId selector ─────────────────────────────────────────────────

  @Test
  void perform_returns200WhenChannelAppIdMatchesSeries() {
    var series = makeSeries("hotfire", "vib_fuel_pump");
    series.setAppId("channel-uuid-1");
    ref.setReferencedTimeseriesList(List.of(
      series,
      makeSeries("hotfire", "vib_lox_pump")
    ));
    var body = new AnomalyDetectRequestIO();
    body.setChannelAppId("channel-uuid-1");
    assertThat(resource.perform(REF_APP_ID, ACTION, body, sc).getStatus()).isEqualTo(200);
    verify(anomalyDetectionService).detect(any(), any(), any());
  }

  @Test
  void perform_returns400WhenChannelAppIdNotFoundInRef() {
    var body = new AnomalyDetectRequestIO();
    body.setChannelAppId("channel-uuid-does-not-exist");
    assertThat(resource.perform(REF_APP_ID, ACTION, body, sc).getStatus()).isEqualTo(400);
  }

  @Test
  void perform_returns422WhenBothChannelAppIdAndTupleProvided() {
    var body = new AnomalyDetectRequestIO();
    body.setChannelAppId("channel-uuid-1");
    body.setMeasurement("hotfire");
    assertThat(resource.perform(REF_APP_ID, ACTION, body, sc).getStatus()).isEqualTo(422);
  }

  @Test
  void perform_tupleSelector_stillWorksWithoutChannelAppId() {
    ref.setReferencedTimeseriesList(List.of(
      makeSeries("hotfire", "vib_fuel_pump"),
      makeSeries("hotfire", "vib_lox_pump")
    ));
    var body = new AnomalyDetectRequestIO();
    body.setSymbolicName("vib_lox_pump");
    assertThat(resource.perform(REF_APP_ID, ACTION, body, sc).getStatus()).isEqualTo(200);
  }

  @Test
  void perform_channelAppIdResolvesByAppIdNotByTuple() {
    var s1 = makeSeries("hotfire", "vib_fuel_pump");
    s1.setAppId("uuid-s1");
    var s2 = makeSeries("hotfire", "vib_fuel_pump");
    s2.setAppId("uuid-s2");
    ref.setReferencedTimeseriesList(List.of(s1, s2));
    var body = new AnomalyDetectRequestIO();
    body.setChannelAppId("uuid-s2");
    assertThat(resource.perform(REF_APP_ID, ACTION, body, sc).getStatus()).isEqualTo(200);
  }

  // ── write permission for createAnnotations ────────────────────────────────

  @Test
  void perform_requires_writePermission_whenCreateAnnotationsTrue() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);
    var body = new AnomalyDetectRequestIO();
    body.setCreateAnnotations(true);
    assertThat(resource.perform(REF_APP_ID, ACTION, body, sc).getStatus()).isEqualTo(403);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private ReferencedTimeseriesNodeEntity makeSeries(String measurement, String symbolicName) {
    var e = new ReferencedTimeseriesNodeEntity();
    e.setMeasurement(measurement);
    e.setSymbolicName(symbolicName);
    e.setField(symbolicName);
    return e;
  }
}
