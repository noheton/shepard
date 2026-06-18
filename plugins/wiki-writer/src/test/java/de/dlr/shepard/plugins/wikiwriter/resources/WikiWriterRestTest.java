package de.dlr.shepard.plugins.wikiwriter.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.plugins.wikiwriter.io.WikiWriteResponseIO;
import de.dlr.shepard.plugins.wikiwriter.services.WikiWriterService;
import de.dlr.shepard.spi.ai.LlmException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link WikiWriterRest} — new path:
 * {@code POST /v2/data-objects/{dataObjectAppId}/wiki-write}.
 */
class WikiWriterRestTest {

  @Mock
  WikiWriterService wikiWriterService;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  @InjectMocks
  WikiWriterRest rest;

  private static final String DO_APP_ID = "01919191-0000-7000-8000-000000000042";
  private static final String CALLER = "testuser";

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  // ── auth gates ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("returns 401 when principal is null")
  void wikiWrite_noPrincipal_returns401() {
    when(sc.getUserPrincipal()).thenReturn(null);

    Response r = rest.wikiWrite(DO_APP_ID, null, sc);

    assertThat(r.getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName("returns 403 when caller lacks write permission")
  void wikiWrite_noPermission_returns403() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Write, CALLER))
      .thenReturn(false);

    Response r = rest.wikiWrite(DO_APP_ID, null, sc);

    assertThat(r.getStatus()).isEqualTo(403);
  }

  // ── LLM guard ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("returns 503 when LLM capability is not available")
  void wikiWrite_llmUnavailable_returns503() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Write, CALLER))
      .thenReturn(true);
    when(wikiWriterService.isAvailable()).thenReturn(false);

    Response r = rest.wikiWrite(DO_APP_ID, null, sc);

    assertThat(r.getStatus()).isEqualTo(503);
  }

  // ── happy path ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("returns 200 with WikiWriteResponseIO on success")
  void wikiWrite_success_returns200() {
    WikiWriteResponseIO responseIO = new WikiWriteResponseIO(99L, "entry-app-id",
      "## TR-004\nSummary.", "activity-id", 100, 50);
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Write, CALLER))
      .thenReturn(true);
    when(wikiWriterService.isAvailable()).thenReturn(true);
    when(wikiWriterService.wikiWriteByDataObjectAppId(eq(DO_APP_ID), any()))
      .thenReturn(responseIO);

    Response r = rest.wikiWrite(DO_APP_ID, null, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(r.getEntity()).isEqualTo(responseIO);
  }

  // ── error paths ───────────────────────────────────────────────────────────

  @Test
  @DisplayName("returns 404 when service throws NotFoundException")
  void wikiWrite_notFound_returns404() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Write, CALLER))
      .thenReturn(true);
    when(wikiWriterService.isAvailable()).thenReturn(true);
    when(wikiWriterService.wikiWriteByDataObjectAppId(eq(DO_APP_ID), any()))
      .thenThrow(new NotFoundException("DataObject not found: " + DO_APP_ID));

    Response r = rest.wikiWrite(DO_APP_ID, null, sc);

    assertThat(r.getStatus()).isEqualTo(404);
  }

  @Test
  @DisplayName("returns 503 when service throws LlmException")
  void wikiWrite_llmException_returns503() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Write, CALLER))
      .thenReturn(true);
    when(wikiWriterService.isAvailable()).thenReturn(true);
    when(wikiWriterService.wikiWriteByDataObjectAppId(eq(DO_APP_ID), any()))
      .thenThrow(new LlmException("upstream timeout"));

    Response r = rest.wikiWrite(DO_APP_ID, null, sc);

    assertThat(r.getStatus()).isEqualTo(503);
  }

  // ── tombstone ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("WikiWriterTombstoneRest returns 410 Gone with Location header")
  void tombstone_returns410() {
    WikiWriterTombstoneRest tombstone = new WikiWriterTombstoneRest();
    String colAppId = "col-app-id";

    Response r = tombstone.wikiWrite(colAppId, DO_APP_ID);

    assertThat(r.getStatus()).isEqualTo(410);
    assertThat(r.getHeaderString("Location")).isEqualTo("/v2/data-objects/" + DO_APP_ID + "/wiki-write");
  }
}
