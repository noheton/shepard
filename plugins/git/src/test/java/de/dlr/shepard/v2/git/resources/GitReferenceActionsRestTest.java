package de.dlr.shepard.v2.git.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.git.adapters.GitAdapterException;
import de.dlr.shepard.context.references.git.daos.GitReferenceDAO;
import de.dlr.shepard.context.references.git.entities.GitReference;
import de.dlr.shepard.context.references.git.entities.GitReferenceMode;
import de.dlr.shepard.context.references.git.io.CheckUpdateResultIO;
import de.dlr.shepard.context.references.git.io.GitArtifactPreviewIO;
import de.dlr.shepard.context.references.git.services.GitReferenceService;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link GitReferenceActionsRest}.
 *
 * <p>APISIMP-GIT-REF-PATH: covers the new
 * {@code GET /v2/references/{appId}/preview} and
 * {@code POST /v2/references/{appId}/check-update} endpoints that replace the
 * retired {@code /v2/data-objects/{dataObjectAppId}/git-references/{appId}/…}
 * paths.
 */
class GitReferenceActionsRestTest {

  static final String DO_APP_ID = "do-appid-1";
  static final String CALLER = "alice";
  static final String GR_APP_ID = "gr-appid-1";

  @Mock
  GitReferenceDAO gitReferenceDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  GitReferenceService gitReferenceService;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  GitReferenceActionsRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new GitReferenceActionsRest();
    resource.gitReferenceDAO = gitReferenceDAO;
    resource.permissionsService = permissionsService;
    resource.gitReferenceService = gitReferenceService;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), any(AccessType.class), eq(CALLER)))
      .thenReturn(true);
  }

  private GitReference existingGr() {
    var parent = new DataObject();
    parent.setAppId(DO_APP_ID);
    var gr = new GitReference("https://gitlab.dlr.de/g/r", "main", "src");
    gr.setAppId(GR_APP_ID);
    gr.setDataObject(parent);
    return gr;
  }

  // ── GET /v2/references/{appId}/preview ────────────────────────────────────

  @Test
  void preview_returns200WithPreviewPayload() {
    GitReference gr = existingGr();
    gr.setMode(GitReferenceMode.TRACKED_ARTIFACT);
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    var preview = new GitArtifactPreviewIO();
    preview.setAvailable(true);
    preview.setSha("abc123");
    preview.setMimeType("text/markdown");
    preview.setContent("# hi");
    when(gitReferenceService.previewArtifact(eq(gr), eq(CALLER))).thenReturn(preview);

    var r = resource.preview(GR_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    assertEquals("abc123", ((GitArtifactPreviewIO) r.getEntity()).getSha());
  }

  @Test
  void preview_returns200_whenNotAvailable_reasonNotUnsupportedHost() {
    GitReference gr = existingGr();
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    var preview = new GitArtifactPreviewIO();
    preview.setAvailable(false);
    preview.setReason("no-credential");
    when(gitReferenceService.previewArtifact(any(), any())).thenReturn(preview);

    var r = resource.preview(GR_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    assertEquals("no-credential", ((GitArtifactPreviewIO) r.getEntity()).getReason());
  }

  @Test
  void preview_returns501ProblemJson_whenUnsupportedHost() {
    GitReference gr = existingGr();
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    var preview = new GitArtifactPreviewIO();
    preview.setAvailable(false);
    preview.setReason("unsupported-host");
    when(gitReferenceService.previewArtifact(any(), any())).thenReturn(preview);

    var r = resource.preview(GR_APP_ID, securityContext);
    assertEquals(501, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
  }

  @Test
  void preview_returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    assertEquals(401, resource.preview(GR_APP_ID, securityContext).getStatus());
  }

  @Test
  void preview_returns403WhenNoReadPermission() {
    GitReference gr = existingGr();
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Read, CALLER))
      .thenReturn(false);
    assertEquals(403, resource.preview(GR_APP_ID, securityContext).getStatus());
  }

  @Test
  void preview_returns404WhenGitReferenceMissing() {
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(null);
    assertEquals(404, resource.preview(GR_APP_ID, securityContext).getStatus());
  }

  // ── POST /v2/references/{appId}/check-update ──────────────────────────────

  @Test
  void checkUpdate_returns200WithResult() {
    GitReference gr = existingGr();
    gr.setMode(GitReferenceMode.TRACKED_ARTIFACT);
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    var result = new CheckUpdateResultIO("newsha", "oldsha", true, System.currentTimeMillis());
    when(gitReferenceService.checkForUpdate(eq(gr), eq(CALLER))).thenReturn(result);

    var r = resource.checkUpdate(GR_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    assertEquals("newsha", ((CheckUpdateResultIO) r.getEntity()).currentSha());
  }

  @Test
  void checkUpdate_returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    assertEquals(401, resource.checkUpdate(GR_APP_ID, securityContext).getStatus());
  }

  @Test
  void checkUpdate_returns403WhenNoWritePermission() {
    GitReference gr = existingGr();
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Write, CALLER))
      .thenReturn(false);
    assertEquals(403, resource.checkUpdate(GR_APP_ID, securityContext).getStatus());
  }

  @Test
  void checkUpdate_returns404WhenGitReferenceMissing() {
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(null);
    assertEquals(404, resource.checkUpdate(GR_APP_ID, securityContext).getStatus());
  }

  @Test
  void checkUpdate_returns502WhenAdapterFails() {
    GitReference gr = existingGr();
    gr.setMode(GitReferenceMode.TRACKED_ARTIFACT);
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    when(gitReferenceService.checkForUpdate(any(), any()))
      .thenThrow(new GitAdapterException(502, "upstream fetch failed"));

    var r = resource.checkUpdate(GR_APP_ID, securityContext);
    assertEquals(502, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
  }
}
