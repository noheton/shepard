package de.dlr.shepard.context.references.git.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
}
