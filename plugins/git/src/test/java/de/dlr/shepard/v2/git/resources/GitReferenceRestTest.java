package de.dlr.shepard.v2.git.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.git.daos.GitReferenceDAO;
import de.dlr.shepard.context.references.git.entities.GitReference;
import de.dlr.shepard.context.references.git.entities.GitReferenceMode;
import de.dlr.shepard.context.references.git.io.GitArtifactPreviewIO;
import de.dlr.shepard.context.references.git.services.GitReferenceService;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link GitReferenceRest}.
 *
 * <p>CRUD (list / create / read / delete / patch) was removed by
 * PLUGIN-PERKIND-CRUD-CLEANUP; those operations are now tested via the
 * generic {@code ReferencesV2Rest} + {@code GitReferenceKindHandler} surface.
 *
 * <p>This class covers only the two domain-specific ops retained here:
 * G1b preview and G1d check-update (check-update tested via integration test).
 */
class GitReferenceRestTest {

  static final String DO_APP_ID = "do-appid-1";
  static final long DO_OGM_ID = 42L;
  static final String CALLER = "alice";
  static final String GR_APP_ID = "gr-appid-1";

  @Mock
  GitReferenceDAO gitReferenceDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  EntityIdResolver entityIdResolver;

  @Mock
  GitReferenceService gitReferenceService;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  GitReferenceRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new GitReferenceRest();
    resource.gitReferenceDAO = gitReferenceDAO;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;
    resource.gitReferenceService = gitReferenceService;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(DO_OGM_ID), any(AccessType.class), eq(CALLER))).thenReturn(true);
  }

  private GitReference existingGr() {
    var parent = new DataObject();
    parent.setAppId(DO_APP_ID);
    var gr = new GitReference("https://gitlab.dlr.de/g/r", "main", "src");
    gr.setAppId(GR_APP_ID);
    gr.setDataObject(parent);
    return gr;
  }

  // ── G1b: GET /{appId}/preview ──────────────────────────────────────────

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

    var r = resource.preview(DO_APP_ID, GR_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    assertEquals("abc123", ((GitArtifactPreviewIO) r.getEntity()).getSha());
  }

  @Test
  void preview_returns200_whenNotAvailable_butReasonNotUnsupportedHost() {
    GitReference gr = existingGr();
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    var preview = new GitArtifactPreviewIO();
    preview.setAvailable(false);
    preview.setReason("no-credential");
    when(gitReferenceService.previewArtifact(any(), any())).thenReturn(preview);

    var r = resource.preview(DO_APP_ID, GR_APP_ID, securityContext);
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

    var r = resource.preview(DO_APP_ID, GR_APP_ID, securityContext);
    assertEquals(501, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
  }

  @Test
  void preview_returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    assertEquals(401, resource.preview(DO_APP_ID, GR_APP_ID, securityContext).getStatus());
  }

  @Test
  void preview_returns403WhenNoReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, CALLER)).thenReturn(false);
    assertEquals(403, resource.preview(DO_APP_ID, GR_APP_ID, securityContext).getStatus());
  }

  @Test
  void preview_returns404WhenGitReferenceMissing() {
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(null);
    assertEquals(404, resource.preview(DO_APP_ID, GR_APP_ID, securityContext).getStatus());
  }

  @Test
  void preview_returns404WhenGitReferenceBelongsToDifferentDataObject() {
    var other = new DataObject();
    other.setAppId("different");
    var gr = new GitReference("https://gitlab.com/g/r", "main", "x");
    gr.setAppId(GR_APP_ID);
    gr.setDataObject(other);
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    assertEquals(404, resource.preview(DO_APP_ID, GR_APP_ID, securityContext).getStatus());
  }
}
