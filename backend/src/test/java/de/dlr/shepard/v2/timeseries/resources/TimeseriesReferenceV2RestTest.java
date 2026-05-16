package de.dlr.shepard.v2.timeseries.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TimeseriesReferenceV2RestTest {

  static final String REF_APP_ID = "ref-appid-tm1";
  static final long DO_OGM_ID = 42L;
  static final String CALLER = "alice";

  @Mock
  TimeseriesReferenceDAO timeseriesReferenceDAO;

  @Mock
  TimeseriesReferenceService timeseriesReferenceService;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  TimeseriesReferenceV2Rest resource;
  TimeseriesReference ref;
  DataObject dataObject;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new TimeseriesReferenceV2Rest();
    resource.timeseriesReferenceDAO = timeseriesReferenceDAO;
    resource.timeseriesReferenceService = timeseriesReferenceService;
    resource.permissionsService = permissionsService;

    dataObject = new DataObject();
    dataObject.setId(DO_OGM_ID);

    ref = new TimeseriesReference();
    ref.setAppId(REF_APP_ID);
    ref.setDataObject(dataObject);

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(timeseriesReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Write, CALLER, 0L)).thenReturn(true);
  }

  // ── 401 / 403 / 404 gates ───────────────────────────────────────────────

  @Test
  void patch_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    var body = patchBody("WALL_CLOCK", null, null);
    assertThat(resource.patch(REF_APP_ID, body, sc).getStatus()).isEqualTo(401);
    verify(timeseriesReferenceService, never()).updateTimeReference(any(), any());
  }

  @Test
  void patch_returns404WhenRefMissing() {
    when(timeseriesReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(null);
    var body = patchBody("WALL_CLOCK", null, null);
    assertThat(resource.patch(REF_APP_ID, body, sc).getStatus()).isEqualTo(404);
    verify(timeseriesReferenceService, never()).updateTimeReference(any(), any());
  }

  @Test
  void patch_returns403WhenNoWritePermission() {
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Write, CALLER, 0L)).thenReturn(false);
    var body = patchBody("WALL_CLOCK", null, null);
    assertThat(resource.patch(REF_APP_ID, body, sc).getStatus()).isEqualTo(403);
    verify(timeseriesReferenceService, never()).updateTimeReference(any(), any());
  }

  // ── 400 validation ──────────────────────────────────────────────────────

  @Test
  void patch_returns400WhenExperimentRelativeWithNoOffsetInPatchOrEntity() {
    // ref has no existing wallClockOffset; patch provides none
    ref.setWallClockOffset(null);
    var body = patchBody("EXPERIMENT_RELATIVE", null, null);
    assertThat(resource.patch(REF_APP_ID, body, sc).getStatus()).isEqualTo(400);
    verify(timeseriesReferenceService, never()).updateTimeReference(any(), any());
  }

  // ── 200 success cases ───────────────────────────────────────────────────

  @Test
  void patch_returns200ForWallClockWithNoOffset() {
    var updated = refWithTimeRef("WALL_CLOCK", null, null);
    when(timeseriesReferenceService.updateTimeReference(eq(ref), any())).thenReturn(updated);
    var body = patchBody("WALL_CLOCK", null, null);
    var r = resource.patch(REF_APP_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    verify(timeseriesReferenceService).updateTimeReference(eq(ref), any());
    var io = (TimeseriesReferenceIO) r.getEntity();
    assertThat(io.getTimeReference()).isEqualTo("WALL_CLOCK");
  }

  @Test
  void patch_returns200ForExperimentRelativeWithOffsetInPatch() {
    var updated = refWithTimeRef("EXPERIMENT_RELATIVE", 1_234_567_890_000L, "manual");
    when(timeseriesReferenceService.updateTimeReference(eq(ref), any())).thenReturn(updated);
    var body = patchBody("EXPERIMENT_RELATIVE", 1_234_567_890_000L, "manual");
    var r = resource.patch(REF_APP_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    verify(timeseriesReferenceService).updateTimeReference(eq(ref), any());
    var io = (TimeseriesReferenceIO) r.getEntity();
    assertThat(io.getTimeReference()).isEqualTo("EXPERIMENT_RELATIVE");
    assertThat(io.getWallClockOffset()).isEqualTo(1_234_567_890_000L);
    assertThat(io.getWallClockOffsetSource()).isEqualTo("manual");
  }

  @Test
  void patch_returns200ForExperimentRelativeWhenOffsetAlreadyStoredOnEntity() {
    // Patch does NOT include offset, but ref already has one stored
    ref.setWallClockOffset(9_999_999L);
    var updated = refWithTimeRef("EXPERIMENT_RELATIVE", 9_999_999L, null);
    when(timeseriesReferenceService.updateTimeReference(eq(ref), any())).thenReturn(updated);
    var body = patchBody("EXPERIMENT_RELATIVE", null, null);
    var r = resource.patch(REF_APP_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    verify(timeseriesReferenceService).updateTimeReference(eq(ref), any());
  }

  @Test
  void patch_returns200WhenOnlyOffsetSourceUpdated() {
    // ref already has WALL_CLOCK set; only provenance tag changes
    ref.setTimeReference("WALL_CLOCK");
    var updated = refWithTimeRef("WALL_CLOCK", null, "ffprobe");
    when(timeseriesReferenceService.updateTimeReference(eq(ref), any())).thenReturn(updated);
    var body = patchBody(null, null, "ffprobe");
    var r = resource.patch(REF_APP_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    var io = (TimeseriesReferenceIO) r.getEntity();
    assertThat(io.getWallClockOffsetSource()).isEqualTo("ffprobe");
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private static TimeseriesReferenceIO patchBody(String timeRef, Long offset, String source) {
    var io = new TimeseriesReferenceIO();
    io.setTimeReference(timeRef);
    io.setWallClockOffset(offset);
    io.setWallClockOffsetSource(source);
    return io;
  }

  private static TimeseriesReference refWithTimeRef(String timeRef, Long offset, String source) {
    var r = new TimeseriesReference();
    r.setTimeReference(timeRef);
    r.setWallClockOffset(offset);
    r.setWallClockOffsetSource(source);
    r.setDataObject(new DataObject());
    return r;
  }
}
