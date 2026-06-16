package de.dlr.shepard.v2.git.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link GitReferenceRest}.
 *
 * <p>APISIMP-GIT-REF-PATH: the old action paths now return 410 Gone. The
 * actual preview + check-update logic moved to {@link GitReferenceActionsRest}
 * (tested by {@link GitReferenceActionsRestTest}).
 */
class GitReferenceRestTest {

  static final String DO_APP_ID = "do-appid-1";
  static final String GR_APP_ID = "gr-appid-1";

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  GitReferenceRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new GitReferenceRest();
  }

  // ── 410 stubs ──────────────────────────────────────────────────────────────

  @Test
  void preview_returns410Gone() {
    var r = resource.preview(DO_APP_ID, GR_APP_ID, securityContext);
    assertEquals(410, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
  }

  @Test
  void checkUpdate_returns410Gone() {
    var r = resource.checkUpdate(DO_APP_ID, GR_APP_ID, securityContext);
    assertEquals(410, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
  }
}
