package de.dlr.shepard.v2.provenance.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.output.OutputProfile;
import de.dlr.shepard.common.output.OutputProfileResolver;
import de.dlr.shepard.provenance.services.ProvenanceService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-PROV-ISO8601-TIMESTAMPS — regression guards for dual-format
 * since/until parsing on {@link ProvenanceRest}.
 *
 * <p>Acceptance criteria: {@code ?since=2026-01-01T00:00:00Z} returns 200;
 * {@code ?since=1751299200000} (epoch-ms) also returns 200; invalid value
 * returns 400.
 */
class ProvenanceRestTimestampTest {

  @Mock
  ProvenanceService provenance;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

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

  // ── parseTimestamp unit tests ──────────────────────────────────────────────

  @Test
  void parseTimestamp_null_returnsNull() {
    assertNull(ProvenanceRest.parseTimestamp(null));
  }

  @Test
  void parseTimestamp_blank_returnsNull() {
    assertNull(ProvenanceRest.parseTimestamp("  "));
  }

  @Test
  void parseTimestamp_epochMs_parsedCorrectly() {
    // 2026-01-01T00:00:00Z = 1 767 225 600 000 ms since epoch
    assertEquals(1_767_225_600_000L, ProvenanceRest.parseTimestamp("1767225600000"));
  }

  @Test
  void parseTimestamp_iso8601_parsedCorrectly() {
    // 2026-01-01T00:00:00Z = 1 767 225 600 000 ms since epoch
    Long result = ProvenanceRest.parseTimestamp("2026-01-01T00:00:00Z");
    assertNotNull(result);
    assertTrue(result > 0L);
    // Cross-check: epoch-ms string for the same instant must agree
    assertEquals(result, ProvenanceRest.parseTimestamp("1767225600000"));
  }

  @Test
  void parseTimestamp_invalidValue_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class,
      () -> ProvenanceRest.parseTimestamp("not-a-timestamp"));
  }

  // ── REST endpoint integration tests ───────────────────────────────────────

  @Test
  void listActivities_iso8601SinceParam_returns200() {
    when(provenance.list(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of());
    Response r = resource.listActivities(null, null, null, "2026-01-01T00:00:00Z", null, 100, securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void listActivities_epochMsSinceParam_returns200() {
    when(provenance.list(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of());
    Response r = resource.listActivities(null, null, null, "1767225600000", null, 100, securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void listActivities_invalidSinceParam_returns400() {
    Response r = resource.listActivities(null, null, null, "not-a-timestamp", null, 100, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void listActivities_iso8601UntilParam_returns200() {
    when(provenance.list(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of());
    Response r = resource.listActivities(null, null, null, null, "2026-12-31T23:59:59Z", 100, securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void listActivities_epochMsUntilParam_returns200() {
    when(provenance.list(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of());
    Response r = resource.listActivities(null, null, null, null, "1767225599000", 100, securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void listActivities_invalidUntilParam_returns400() {
    Response r = resource.listActivities(null, null, null, null, "bad-until", 100, securityContext);
    assertEquals(400, r.getStatus());
  }
}
