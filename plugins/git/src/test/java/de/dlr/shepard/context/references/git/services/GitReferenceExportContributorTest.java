package de.dlr.shepard.context.references.git.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.services.GitCredentialService;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.export.ExportBuilder;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.git.adapters.GitAdapter;
import de.dlr.shepard.context.references.git.adapters.GitAdapterRegistry;
import de.dlr.shepard.context.references.git.entities.GitReference;
import de.dlr.shepard.context.references.git.entities.GitReferenceMode;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link GitReferenceExportContributor} (G1c).
 */
class GitReferenceExportContributorTest {

  static final String REPO_URL = "https://gitlab.dlr.de/group/repo";
  static final String REF = "main";
  static final String PATH = "src/analysis.py";
  static final String SHA = "abc123def456";
  static final String CALLER = "alice";

  @Mock
  GitCredentialService gitCredentialService;

  @Mock
  GitAdapterRegistry gitAdapterRegistry;

  @Mock
  ExportBuilder builder;

  @Mock
  GitAdapter gitAdapter;

  GitReferenceExportContributor contributor;

  @BeforeEach
  void setUp() throws java.io.IOException {
    MockitoAnnotations.openMocks(this);
    contributor = new GitReferenceExportContributor();
    contributor.gitCredentialService = gitCredentialService;
    contributor.gitAdapterRegistry = gitAdapterRegistry;
    // Default builder stubs so chaining works
    when(builder.addReference(any(), any())).thenReturn(builder);
    when(builder.addSoftwareSourceCode(any(), any(), any(), any(), any())).thenReturn(builder);
  }

  // ── handles() ──────────────────────────────────────────────────────────────

  @Test
  void handles_GitReference_returnsTrue() {
    assertTrue(contributor.handles("GitReference"));
  }

  @Test
  void handles_FileReference_returnsFalse() {
    assertFalse(contributor.handles("FileReference"));
  }

  @Test
  void handles_null_returnsFalse() {
    assertFalse(contributor.handles(null));
  }

  // ── contribute — PINNED_SNAPSHOT with stored sha ───────────────────────────

  @Test
  void contribute_pinnedSnapshot_addsJsonAndSoftwareSourceCode() throws IOException {
    GitReference gr = makeGr(GitReferenceMode.PINNED_SNAPSHOT, REF, PATH);
    gr.setSha(SHA); // server-managed pinned SHA

    contributor.contribute(builder, gr, CALLER);

    // JSON sidecar must be written
    verify(builder).addReference(any(BasicReferenceIO.class), any());
    // SoftwareSourceCode entity must cite the pinned SHA
    ArgumentCaptor<String> idCap = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> shaCap = ArgumentCaptor.forClass(String.class);
    verify(builder).addSoftwareSourceCode(idCap.capture(), eq(REPO_URL), eq(PATH), shaCap.capture(), eq(REF));
    assertTrue(idCap.getValue().contains(SHA), "entity @id should contain the SHA");
    assertTrue(idCap.getValue().contains(PATH.replaceAll("^/+", "")), "entity @id should contain the path");
    // No live adapter call needed when sha is already stored
    verify(gitAdapterRegistry, never()).findByHost(any());
  }

  // ── contribute — TRACKED_ARTIFACT with cached resolvedSha ──────────────────

  @Test
  void contribute_trackedArtifactWithResolvedSha_usesCachedSha() throws IOException {
    GitReference gr = makeGr(GitReferenceMode.TRACKED_ARTIFACT, REF, PATH);
    gr.setResolvedSha(SHA); // last-fetch SHA already stored

    contributor.contribute(builder, gr, CALLER);

    verify(builder).addReference(any(BasicReferenceIO.class), any());
    ArgumentCaptor<String> shaCap = ArgumentCaptor.forClass(String.class);
    verify(builder).addSoftwareSourceCode(any(), eq(REPO_URL), eq(PATH), shaCap.capture(), eq(REF));
    assertTrue(SHA.equals(shaCap.getValue()));
    // resolvedSha was present — no live resolution needed
    verify(gitAdapterRegistry, never()).findByHost(any());
  }

  // ── contribute — LOOSE_LINK with no sha ────────────────────────────────────

  @Test
  void contribute_looseLinkNoSha_usesRepoUrlAsId() throws IOException {
    GitReference gr = makeGr(GitReferenceMode.LOOSE_LINK, REF, null);
    // no sha, no resolvedSha

    contributor.contribute(builder, gr, CALLER);

    verify(builder).addReference(any(BasicReferenceIO.class), any());
    ArgumentCaptor<String> idCap = ArgumentCaptor.forClass(String.class);
    verify(builder).addSoftwareSourceCode(idCap.capture(), eq(REPO_URL), isNull(), isNull(), eq(REF));
    // Without sha, the id falls back to the base repoUrl
    assertTrue(idCap.getValue().equals(REPO_URL), "entity @id should be the bare repoUrl for loose-link without SHA");
  }

  // ── contribute — best-effort resolution ────────────────────────────────────

  @Test
  void contribute_bestEffortResolution_adaptersQueried() throws IOException {
    GitReference gr = makeGr(GitReferenceMode.TRACKED_ARTIFACT, REF, PATH);
    // no resolvedSha — need live resolution
    when(gitAdapterRegistry.findByHost("gitlab.dlr.de")).thenReturn(Optional.of(gitAdapter));
    when(gitCredentialService.findPatForHost(CALLER, "gitlab.dlr.de")).thenReturn(Optional.of("my-pat"));
    when(gitAdapter.resolveRef(REPO_URL, REF, "my-pat")).thenReturn(SHA);

    contributor.contribute(builder, gr, CALLER);

    verify(gitAdapterRegistry).findByHost("gitlab.dlr.de");
    ArgumentCaptor<String> shaCap = ArgumentCaptor.forClass(String.class);
    verify(builder).addSoftwareSourceCode(any(), eq(REPO_URL), eq(PATH), shaCap.capture(), eq(REF));
    assertTrue(SHA.equals(shaCap.getValue()), "Best-effort resolved SHA should be used");
  }

  @Test
  void contribute_bestEffortResolution_missingCredential_shaIsNull() throws IOException {
    GitReference gr = makeGr(GitReferenceMode.TRACKED_ARTIFACT, REF, PATH);
    when(gitAdapterRegistry.findByHost("gitlab.dlr.de")).thenReturn(Optional.of(gitAdapter));
    when(gitCredentialService.findPatForHost(CALLER, "gitlab.dlr.de")).thenReturn(Optional.empty());

    contributor.contribute(builder, gr, CALLER);

    // sha should be null (no credential → skip)
    verify(builder).addSoftwareSourceCode(any(), eq(REPO_URL), eq(PATH), isNull(), eq(REF));
    // adapter resolveRef must NOT be called when PAT is absent
    verify(gitAdapter, never()).resolveRef(any(), any(), any());
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  /** Creates a GitReference with a synthetic parent DataObject so BasicReferenceIO doesn't NPE. */
  private GitReference makeGr(GitReferenceMode mode, String ref, String path) {
    GitReference gr = new GitReference(REPO_URL, ref, path);
    gr.setMode(mode);
    DataObject parent = new DataObject();
    parent.setShepardId(1L);
    gr.setDataObject(parent);
    return gr;
  }
}
