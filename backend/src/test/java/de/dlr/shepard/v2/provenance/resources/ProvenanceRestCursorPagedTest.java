package de.dlr.shepard.v2.provenance.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.output.OutputProfile;
import de.dlr.shepard.common.output.OutputProfileResolver;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-PROV-CURSOR-PAGED-WRAP — regression guards ensuring that
 * {@link ProvenanceRest#listActivities} and
 * {@link ProvenanceRest#listEntityActivities} use the request {@code limit}
 * as {@code pageSize} in the envelope (not {@code rows.size()}) and emit
 * correct {@code X-Has-More} / {@code X-Next-Cursor} response headers.
 */
class ProvenanceRestCursorPagedTest {

  @Mock ProvenanceService provenance;
  @Mock SecurityContext securityContext;
  @Mock Principal principal;

  ProvenanceRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new ProvenanceRest();
    resource.provenance = provenance;
    OutputProfileResolver outputProfile = new OutputProfileResolver();
    outputProfile.setProfile(OutputProfile.ALL);
    resource.outputProfile = outputProfile;

    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn("alice");
    when(securityContext.isUserInRole(any())).thenReturn(false);
  }

  private static Activity stubActivity(long startedAtMillis) {
    Activity a = mock(Activity.class);
    when(a.getStartedAtMillis()).thenReturn(startedAtMillis);
    when(a.getAgentUsername()).thenReturn("alice");
    when(a.getActionKind()).thenReturn("READ");
    return a;
  }

  // ── listActivities ────────────────────────────────────────────────────────

  @Test
  void listActivities_emptyResult_pageSizeEqualsLimit() {
    when(provenance.list(any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of());
    Response r = resource.listActivities(null, null, null, null, null, 50, securityContext);
    assertEquals(200, r.getStatus());
    PagedResponseIO<?> body = (PagedResponseIO<?>) r.getEntity();
    assertEquals(50, body.pageSize(), "pageSize must equal the limit param (50), not rows.size() (0)");
  }

  @Test
  void listActivities_emptyResult_hasMoreFalse() {
    when(provenance.list(any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of());
    Response r = resource.listActivities(null, null, null, null, null, 10, securityContext);
    assertEquals("false", r.getHeaderString("X-Has-More"));
  }

  @Test
  void listActivities_emptyResult_noNextCursor() {
    when(provenance.list(any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of());
    Response r = resource.listActivities(null, null, null, null, null, 10, securityContext);
    assertNull(r.getHeaderString("X-Next-Cursor"), "X-Next-Cursor must be absent when hasMore=false");
  }

  @Test
  void listActivities_fullWindow_hasMoreTrue() {
    // Returning exactly `limit` rows signals the window is full → hasMore=true.
    Activity a1 = stubActivity(2000L);
    Activity a2 = stubActivity(1000L);
    when(provenance.list(any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of(a1, a2));
    Response r = resource.listActivities(null, null, null, null, null, 2, securityContext);
    assertEquals("true", r.getHeaderString("X-Has-More"));
  }

  @Test
  void listActivities_fullWindow_nextCursorIsOldestRow() {
    // Rows sorted DESC by startedAt; last row has the oldest timestamp.
    Activity a1 = stubActivity(2000L);
    Activity a2 = stubActivity(1000L);
    when(provenance.list(any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of(a1, a2));
    Response r = resource.listActivities(null, null, null, null, null, 2, securityContext);
    assertNotNull(r.getHeaderString("X-Next-Cursor"));
    assertEquals("1000", r.getHeaderString("X-Next-Cursor"),
        "X-Next-Cursor must be the oldest row's startedAtMillis (use as ?until= for next page)");
  }

  @Test
  void listActivities_partialWindow_hasMoreFalse() {
    // Returning fewer rows than limit → window not full → hasMore=false.
    Activity a1 = stubActivity(5000L);
    when(provenance.list(any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of(a1));
    Response r = resource.listActivities(null, null, null, null, null, 10, securityContext);
    assertEquals("false", r.getHeaderString("X-Has-More"));
    assertNull(r.getHeaderString("X-Next-Cursor"));
  }

  // ── listEntityActivities ──────────────────────────────────────────────────

  @Test
  void listEntityActivities_emptyResult_pageSizeEqualsLimit() {
    when(provenance.list(any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of());
    Response r = resource.listEntityActivities("some-appid", null, null, 50, securityContext);
    assertEquals(200, r.getStatus());
    PagedResponseIO<?> body = (PagedResponseIO<?>) r.getEntity();
    assertEquals(50, body.pageSize());
  }

  @Test
  void listEntityActivities_fullWindow_hasMoreTrue() {
    Activity a1 = stubActivity(9000L);
    Activity a2 = stubActivity(8000L);
    when(provenance.list(any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of(a1, a2));
    Response r = resource.listEntityActivities("e1", null, null, 2, securityContext);
    assertEquals("true", r.getHeaderString("X-Has-More"));
    assertEquals("8000", r.getHeaderString("X-Next-Cursor"));
  }
}
