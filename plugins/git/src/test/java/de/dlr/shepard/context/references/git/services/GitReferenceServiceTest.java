package de.dlr.shepard.context.references.git.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.services.GitCredentialService;
import de.dlr.shepard.context.references.git.adapters.FileResolution;
import de.dlr.shepard.context.references.git.adapters.GitAdapter;
import de.dlr.shepard.context.references.git.adapters.GitAdapterException;
import de.dlr.shepard.context.references.git.adapters.GitAdapterRegistry;
import de.dlr.shepard.context.references.git.entities.GitReference;
import de.dlr.shepard.context.references.git.entities.GitReferenceMode;
import de.dlr.shepard.context.references.git.io.CheckUpdateResultIO;
import de.dlr.shepard.context.references.git.io.GitArtifactPreviewIO;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GitReferenceServiceTest {

  private GitArtifactCache realCache() {
    // Use a direct delegate: this isn't @QuarkusTest, so we sidestep
    // the @CacheResult annotation by wrapping a fresh ApplicationScoped
    // bean that runs the body inline.
    return new GitArtifactCache() {
      @Override
      public FileResolution getFile(String userSub, String repoUrl, String ref, String path, GitAdapter adapter, String pat) {
        return adapter.getFile(repoUrl, ref, path, pat);
      }
    };
  }

  private GitReference trackedRef() {
    GitReference gr = new GitReference("https://gitlab.com/foo/bar", "main", "README.md");
    gr.setMode(GitReferenceMode.TRACKED_ARTIFACT);
    return gr;
  }

  @Test
  void preview_happyPath_inlinesContent() {
    GitCredentialService creds = mock(GitCredentialService.class);
    when(creds.findPatForHost("alice", "gitlab.com")).thenReturn(Optional.of("PAT"));

    GitAdapter adapter = mock(GitAdapter.class);
    when(adapter.getFile(anyString(), anyString(), anyString(), anyString()))
      .thenReturn(new FileResolution("sha", "# hi".getBytes(), "text/markdown", 4L));

    GitAdapterRegistry registry = mock(GitAdapterRegistry.class);
    when(registry.findByHost("gitlab.com")).thenReturn(Optional.of(adapter));

    GitReferenceService svc = new GitReferenceService(creds, registry, realCache(), 1_048_576L);
    GitArtifactPreviewIO out = svc.previewArtifact(trackedRef(), "alice");

    assertTrue(out.isAvailable());
    assertEquals("sha", out.getSha());
    assertEquals("text/markdown", out.getMimeType());
    assertEquals(4L, out.getByteSize());
    assertEquals("# hi", out.getContent());
    assertFalse(out.isContentTruncated());
  }

  @Test
  void preview_noPat_returnsAvailableFalseWithReason() {
    GitCredentialService creds = mock(GitCredentialService.class);
    when(creds.findPatForHost(anyString(), anyString())).thenReturn(Optional.empty());

    GitAdapter adapter = mock(GitAdapter.class);
    GitAdapterRegistry registry = mock(GitAdapterRegistry.class);
    when(registry.findByHost("gitlab.com")).thenReturn(Optional.of(adapter));

    GitReferenceService svc = new GitReferenceService(creds, registry, realCache(), 1_048_576L);
    GitArtifactPreviewIO out = svc.previewArtifact(trackedRef(), "alice");
    assertFalse(out.isAvailable());
    assertEquals("no-credential", out.getReason());
    assertNull(out.getContent());
  }

  @Test
  void preview_modeIsLoose_returnsNotTracked() {
    GitReference gr = new GitReference("https://gitlab.com/foo/bar", "main", "x");
    gr.setMode(GitReferenceMode.LOOSE_LINK);

    GitCredentialService creds = mock(GitCredentialService.class);
    GitAdapterRegistry registry = mock(GitAdapterRegistry.class);
    GitReferenceService svc = new GitReferenceService(creds, registry, realCache(), 1_048_576L);

    GitArtifactPreviewIO out = svc.previewArtifact(gr, "alice");
    assertFalse(out.isAvailable());
    assertEquals("not-tracked", out.getReason());
  }

  @Test
  void preview_missingRefOrPath_returnsNotTracked() {
    GitReference gr = new GitReference("https://gitlab.com/foo/bar", null, "x");
    gr.setMode(GitReferenceMode.TRACKED_ARTIFACT);

    GitReferenceService svc = new GitReferenceService(mock(GitCredentialService.class), mock(GitAdapterRegistry.class), realCache(), 1_048_576L);
    GitArtifactPreviewIO out = svc.previewArtifact(gr, "alice");
    assertEquals("not-tracked", out.getReason());
  }

  @Test
  void preview_unsupportedHost_returnsUnsupportedHost() {
    GitCredentialService creds = mock(GitCredentialService.class);
    GitAdapterRegistry registry = mock(GitAdapterRegistry.class);
    when(registry.findByHost(anyString())).thenReturn(Optional.empty());

    GitReference gr = new GitReference("https://github.com/foo/bar", "main", "x");
    gr.setMode(GitReferenceMode.TRACKED_ARTIFACT);

    GitReferenceService svc = new GitReferenceService(creds, registry, realCache(), 1_048_576L);
    GitArtifactPreviewIO out = svc.previewArtifact(gr, "alice");
    assertFalse(out.isAvailable());
    assertEquals("unsupported-host", out.getReason());
  }

  @Test
  void preview_oversize_returnsTruncated() {
    byte[] big = new byte[100];
    GitCredentialService creds = mock(GitCredentialService.class);
    when(creds.findPatForHost(eq("alice"), eq("gitlab.com"))).thenReturn(Optional.of("PAT"));
    GitAdapter adapter = mock(GitAdapter.class);
    when(adapter.getFile(any(), any(), any(), any()))
      .thenReturn(new FileResolution("sha", big, "application/octet-stream", 100L));
    GitAdapterRegistry registry = mock(GitAdapterRegistry.class);
    when(registry.findByHost("gitlab.com")).thenReturn(Optional.of(adapter));

    GitReferenceService svc = new GitReferenceService(creds, registry, realCache(), 50L); // cap at 50 bytes
    GitArtifactPreviewIO out = svc.previewArtifact(trackedRef(), "alice");
    assertTrue(out.isAvailable());
    assertTrue(out.isContentTruncated());
    assertNull(out.getContent());
    assertEquals(100L, out.getByteSize());
  }

  @Test
  void preview_adapterAlreadyTruncated_passesThrough() {
    GitCredentialService creds = mock(GitCredentialService.class);
    when(creds.findPatForHost(any(), any())).thenReturn(Optional.of("PAT"));
    GitAdapter adapter = mock(GitAdapter.class);
    when(adapter.getFile(any(), any(), any(), any()))
      .thenReturn(new FileResolution("sha", null, "application/octet-stream", 999_999L));
    GitAdapterRegistry registry = mock(GitAdapterRegistry.class);
    when(registry.findByHost("gitlab.com")).thenReturn(Optional.of(adapter));

    GitReferenceService svc = new GitReferenceService(creds, registry, realCache(), 1_048_576L);
    GitArtifactPreviewIO out = svc.previewArtifact(trackedRef(), "alice");
    assertTrue(out.isAvailable());
    assertTrue(out.isContentTruncated());
    assertNull(out.getContent());
  }

  @Test
  void preview_adapterThrows_returnsFetchFailed() {
    GitCredentialService creds = mock(GitCredentialService.class);
    when(creds.findPatForHost(any(), any())).thenReturn(Optional.of("PAT"));
    GitAdapter adapter = mock(GitAdapter.class);
    when(adapter.getFile(any(), any(), any(), any())).thenThrow(new GitAdapterException(404, "boom"));
    GitAdapterRegistry registry = mock(GitAdapterRegistry.class);
    when(registry.findByHost("gitlab.com")).thenReturn(Optional.of(adapter));

    GitReferenceService svc = new GitReferenceService(creds, registry, realCache(), 1_048_576L);
    GitArtifactPreviewIO out = svc.previewArtifact(trackedRef(), "alice");
    assertFalse(out.isAvailable());
    assertEquals("fetch-failed", out.getReason());
  }

  @Test
  void preview_nullRef_returnsNotFound() {
    GitReferenceService svc = new GitReferenceService(mock(GitCredentialService.class), mock(GitAdapterRegistry.class), realCache(), 1_048_576L);
    var out = svc.previewArtifact(null, "alice");
    assertFalse(out.isAvailable());
    assertEquals("not-found", out.getReason());
  }

  @Test
  void preview_invalidRepoUrl_returnsInvalidRepoUrl() {
    GitReference gr = new GitReference("not a uri at all!!!", "main", "x");
    gr.setMode(GitReferenceMode.TRACKED_ARTIFACT);
    GitReferenceService svc = new GitReferenceService(mock(GitCredentialService.class), mock(GitAdapterRegistry.class), realCache(), 1_048_576L);
    var out = svc.previewArtifact(gr, "alice");
    assertFalse(out.isAvailable());
    assertEquals("invalid-repo-url", out.getReason());
  }

  // ── pinSnapshot ────────────────────────────────────────────────────────────

  @Test
  void pinSnapshot_blankRef_throws400() {
    GitReference gr = new GitReference("https://gitlab.com/foo/bar", null, "x");
    gr.setMode(GitReferenceMode.PINNED_SNAPSHOT);
    GitReferenceService svc = new GitReferenceService(mock(GitCredentialService.class), mock(GitAdapterRegistry.class), realCache(), 1_048_576L);
    GitAdapterException ex = assertThrows(GitAdapterException.class, () -> svc.pinSnapshot(gr, "alice"));
    assertEquals(400, ex.getStatus());
  }

  @Test
  void pinSnapshot_noAdapter_throws400() {
    GitReference gr = new GitReference("https://gitlab.com/foo/bar", "main", "x");
    gr.setMode(GitReferenceMode.PINNED_SNAPSHOT);
    GitAdapterRegistry registry = mock(GitAdapterRegistry.class);
    when(registry.findByHost("gitlab.com")).thenReturn(Optional.empty());
    GitReferenceService svc = new GitReferenceService(mock(GitCredentialService.class), registry, realCache(), 1_048_576L);
    GitAdapterException ex = assertThrows(GitAdapterException.class, () -> svc.pinSnapshot(gr, "alice"));
    assertEquals(400, ex.getStatus());
  }

  @Test
  void pinSnapshot_noPat_throws400() {
    GitReference gr = new GitReference("https://gitlab.com/foo/bar", "main", "x");
    gr.setMode(GitReferenceMode.PINNED_SNAPSHOT);
    GitAdapter adapter = mock(GitAdapter.class);
    GitAdapterRegistry registry = mock(GitAdapterRegistry.class);
    when(registry.findByHost("gitlab.com")).thenReturn(Optional.of(adapter));
    GitCredentialService creds = mock(GitCredentialService.class);
    when(creds.findPatForHost("alice", "gitlab.com")).thenReturn(Optional.empty());
    GitReferenceService svc = new GitReferenceService(creds, registry, realCache(), 1_048_576L);
    GitAdapterException ex = assertThrows(GitAdapterException.class, () -> svc.pinSnapshot(gr, "alice"));
    assertEquals(400, ex.getStatus());
  }

  @Test
  void pinSnapshot_adapterFails_throws502() {
    GitReference gr = new GitReference("https://gitlab.com/foo/bar", "main", "x");
    gr.setMode(GitReferenceMode.PINNED_SNAPSHOT);
    GitAdapter adapter = mock(GitAdapter.class);
    when(adapter.resolveRef(anyString(), eq("main"), eq("PAT"))).thenThrow(new GitAdapterException(404, "not found"));
    GitAdapterRegistry registry = mock(GitAdapterRegistry.class);
    when(registry.findByHost("gitlab.com")).thenReturn(Optional.of(adapter));
    GitCredentialService creds = mock(GitCredentialService.class);
    when(creds.findPatForHost("alice", "gitlab.com")).thenReturn(Optional.of("PAT"));
    GitReferenceService svc = new GitReferenceService(creds, registry, realCache(), 1_048_576L);
    GitAdapterException ex = assertThrows(GitAdapterException.class, () -> svc.pinSnapshot(gr, "alice"));
    assertEquals(502, ex.getStatus());
  }

  @Test
  void pinSnapshot_success_freezesShaOnEntity() {
    GitReference gr = new GitReference("https://gitlab.com/foo/bar", "main", "x");
    gr.setMode(GitReferenceMode.PINNED_SNAPSHOT);
    GitAdapter adapter = mock(GitAdapter.class);
    when(adapter.resolveRef(anyString(), eq("main"), eq("PAT"))).thenReturn("deadbeef123");
    GitAdapterRegistry registry = mock(GitAdapterRegistry.class);
    when(registry.findByHost("gitlab.com")).thenReturn(Optional.of(adapter));
    GitCredentialService creds = mock(GitCredentialService.class);
    when(creds.findPatForHost("alice", "gitlab.com")).thenReturn(Optional.of("PAT"));
    GitReferenceService svc = new GitReferenceService(creds, registry, realCache(), 1_048_576L);

    String sha = svc.pinSnapshot(gr, "alice");

    assertEquals("deadbeef123", sha);
    assertEquals("deadbeef123", gr.getSha());
    assertEquals("deadbeef123", gr.getResolvedSha());
    assertNotNull(gr.getResolvedAtMillis());
    assertTrue(gr.getResolvedAtMillis() > 0);
  }

  // ── checkForUpdate (G1d) ────────────────────────────────────────────────

  private GitReferenceService svcForCheckUpdate(GitAdapter adapter, GitCredentialService creds) {
    GitAdapterRegistry registry = mock(GitAdapterRegistry.class);
    when(registry.findByHost("gitlab.com")).thenReturn(Optional.of(adapter));
    return new GitReferenceService(creds, registry, realCache(), 1_048_576L);
  }

  @Test
  void checkForUpdate_blankRef_throws400() {
    GitReference gr = new GitReference("https://gitlab.com/foo/bar", null, null);
    GitReferenceService svc = svcForCheckUpdate(mock(GitAdapter.class), mock(GitCredentialService.class));
    GitAdapterException ex = assertThrows(GitAdapterException.class, () -> svc.checkForUpdate(gr, "alice"));
    assertEquals(400, ex.getStatus());
  }

  @Test
  void checkForUpdate_noAdapter_throws400() {
    GitReference gr = new GitReference("https://example.invalid/foo/bar", "main", null);
    GitAdapterRegistry registry = mock(GitAdapterRegistry.class);
    when(registry.findByHost(anyString())).thenReturn(Optional.empty());
    GitReferenceService svc = new GitReferenceService(
      mock(GitCredentialService.class), registry, realCache(), 1_048_576L);
    GitAdapterException ex = assertThrows(GitAdapterException.class, () -> svc.checkForUpdate(gr, "alice"));
    assertEquals(400, ex.getStatus());
  }

  @Test
  void checkForUpdate_publicRepoNoPat_resolvesAnonymously() {
    // The check-update path tolerates a missing PAT — public-repo flows
    // hit upstream anonymously. This is the key difference vs pinSnapshot.
    GitReference gr = new GitReference("https://gitlab.com/foo/bar", "main", null);
    gr.setMode(GitReferenceMode.LOOSE_LINK);
    GitAdapter adapter = mock(GitAdapter.class);
    when(adapter.resolveRef(anyString(), eq("main"), isNullPat())).thenReturn("abc123");
    GitCredentialService creds = mock(GitCredentialService.class);
    when(creds.findPatForHost(anyString(), anyString())).thenReturn(Optional.empty());

    CheckUpdateResultIO out = svcForCheckUpdate(adapter, creds).checkForUpdate(gr, "alice");

    assertEquals("abc123", out.currentSha());
    assertNull(out.previousSha());
    assertFalse(out.updated());                  // previousSha null → not "updated"
    assertTrue(out.checkedAtMillis() > 0);
    assertEquals("abc123", gr.getResolvedSha()); // side-effect persisted on entity
    assertNotNull(gr.getResolvedAtMillis());
  }

  @Test
  void checkForUpdate_shaUnchanged_returnsUpdatedFalse() {
    GitReference gr = new GitReference("https://gitlab.com/foo/bar", "main", null);
    gr.setMode(GitReferenceMode.LOOSE_LINK);
    gr.setResolvedSha("same-sha");
    gr.setResolvedAtMillis(100L);

    GitAdapter adapter = mock(GitAdapter.class);
    when(adapter.resolveRef(anyString(), eq("main"), eq("PAT"))).thenReturn("same-sha");
    GitCredentialService creds = mock(GitCredentialService.class);
    when(creds.findPatForHost("alice", "gitlab.com")).thenReturn(Optional.of("PAT"));

    CheckUpdateResultIO out = svcForCheckUpdate(adapter, creds).checkForUpdate(gr, "alice");

    assertEquals("same-sha", out.currentSha());
    assertEquals("same-sha", out.previousSha());
    assertFalse(out.updated());
    assertTrue(gr.getResolvedAtMillis() > 100L); // timestamp still refreshed
  }

  @Test
  void checkForUpdate_shaMoved_returnsUpdatedTrue() {
    GitReference gr = new GitReference("https://gitlab.com/foo/bar", "main", null);
    gr.setMode(GitReferenceMode.LOOSE_LINK);
    gr.setResolvedSha("old-sha");
    gr.setResolvedAtMillis(100L);

    GitAdapter adapter = mock(GitAdapter.class);
    when(adapter.resolveRef(anyString(), eq("main"), eq("PAT"))).thenReturn("new-sha");
    GitCredentialService creds = mock(GitCredentialService.class);
    when(creds.findPatForHost("alice", "gitlab.com")).thenReturn(Optional.of("PAT"));

    CheckUpdateResultIO out = svcForCheckUpdate(adapter, creds).checkForUpdate(gr, "alice");

    assertEquals("new-sha", out.currentSha());
    assertEquals("old-sha", out.previousSha());
    assertTrue(out.updated());
    assertEquals("new-sha", gr.getResolvedSha());
  }

  @Test
  void checkForUpdate_adapterFails_throws502() {
    GitReference gr = new GitReference("https://gitlab.com/foo/bar", "main", null);
    gr.setMode(GitReferenceMode.LOOSE_LINK);

    GitAdapter adapter = mock(GitAdapter.class);
    when(adapter.resolveRef(anyString(), anyString(), any()))
      .thenThrow(new GitAdapterException(404, "ref not found"));
    GitCredentialService creds = mock(GitCredentialService.class);
    when(creds.findPatForHost(anyString(), anyString())).thenReturn(Optional.empty());

    GitAdapterException ex = assertThrows(GitAdapterException.class,
      () -> svcForCheckUpdate(adapter, creds).checkForUpdate(gr, "alice"));
    assertEquals(502, ex.getStatus());
  }

  /** Mockito matcher: stub on a null PAT argument explicitly so the
      "no-PAT" path goes through {@code resolveRef(..., null)}. */
  private static String isNullPat() {
    return org.mockito.ArgumentMatchers.<String>isNull();
  }
}
