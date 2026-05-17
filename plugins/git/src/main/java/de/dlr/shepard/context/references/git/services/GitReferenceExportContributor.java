package de.dlr.shepard.context.references.git.services;

import de.dlr.shepard.auth.users.services.GitCredentialService;
import de.dlr.shepard.context.export.ExportBuilder;
import de.dlr.shepard.context.export.ExportContributor;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.git.adapters.GitAdapter;
import de.dlr.shepard.context.references.git.adapters.GitAdapterException;
import de.dlr.shepard.context.references.git.adapters.GitAdapterRegistry;
import de.dlr.shepard.context.references.git.adapters.ParsedRepoUrl;
import de.dlr.shepard.context.references.git.entities.GitReference;
import de.dlr.shepard.context.references.git.entities.GitReferenceMode;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Optional;

/**
 * G1c {@link ExportContributor} that handles {@code GitReference} rows in the RO-Crate
 * export pipeline. For each {@code GitReference} it:
 * <ol>
 *   <li>Emits the standard JSON sidecar via {@link ExportBuilder#addReference}.</li>
 *   <li>Adds a {@code schema:SoftwareSourceCode} contextual entity with the resolved
 *       commit SHA (pinned or best-effort) via {@link ExportBuilder#addSoftwareSourceCode}.</li>
 * </ol>
 *
 * <p>SHA resolution order:
 * <ol>
 *   <li>{@link GitReference#getSha()} — frozen by PINNED_SNAPSHOT creation; never null once set.</li>
 *   <li>{@link GitReference#getResolvedSha()} — last fetch of a TRACKED_ARTIFACT; may be stale but
 *       better than nothing.</li>
 *   <li>Best-effort live resolution — only when neither of the above is available and the reference
 *       has a non-blank {@code ref} and is not LOOSE_LINK. Calls the registered {@link GitAdapter}
 *       with the caller's PAT; silently skips on any failure (export must not fail because a git
 *       host is unreachable).</li>
 * </ol>
 */
@ApplicationScoped
public class GitReferenceExportContributor implements ExportContributor {

  @Inject
  GitCredentialService gitCredentialService;

  @Inject
  GitAdapterRegistry gitAdapterRegistry;

  @Override
  public boolean handles(String referenceType) {
    return "GitReference".equals(referenceType);
  }

  @Override
  public void contribute(ExportBuilder builder, BasicReference reference, String callerUsername) throws IOException {
    GitReference gr = (GitReference) reference;

    // Emit the JSON sidecar file (same shape as the generic BasicReference export).
    builder.addReference(new BasicReferenceIO(gr), gr.getCreatedBy());

    if (gr.getRepoUrl() == null || gr.getRepoUrl().isBlank()) return;

    // Determine the SHA to cite in the SoftwareSourceCode entity.
    String sha = gr.getSha(); // PINNED_SNAPSHOT — frozen at create/patch time
    if (sha == null) sha = gr.getResolvedSha(); // TRACKED_ARTIFACT last-fetch
    if (sha == null && gr.getMode() != GitReferenceMode.LOOSE_LINK && gr.getRef() != null && !gr.getRef().isBlank()) {
      sha = resolveShaBestEffort(gr, callerUsername);
    }

    String entityId = buildEntityId(gr, sha);
    builder.addSoftwareSourceCode(entityId, gr.getRepoUrl(), gr.getPath(), sha, gr.getRef());
  }

  /**
   * Attempts a live SHA resolution at export time. Silences all exceptions so a temporary
   * network failure or missing credential does not abort the export — the SHA will simply be
   * absent from the RO-Crate's SoftwareSourceCode entity.
   */
  private String resolveShaBestEffort(GitReference gr, String callerUsername) {
    try {
      ParsedRepoUrl parsed = ParsedRepoUrl.parse(gr.getRepoUrl());
      Optional<GitAdapter> adapter = gitAdapterRegistry.findByHost(parsed.host());
      Optional<String> pat = gitCredentialService.findPatForHost(callerUsername, parsed.host());
      if (adapter.isPresent() && pat.isPresent()) {
        return adapter.get().resolveRef(gr.getRepoUrl(), gr.getRef(), pat.get());
      }
    } catch (Exception ex) {
      Log.debugf("G1c: best-effort SHA resolution failed at export time: %s", ex.getMessage());
    }
    return null;
  }

  /**
   * Builds the {@code @id} for the SoftwareSourceCode contextual entity. The goal is an
   * immutable permalink when enough information is available:
   * <ul>
   *   <li>SHA + path → blob URL (most specific, fully immutable).</li>
   *   <li>SHA only → commit URL.</li>
   *   <li>ref + path → ref-based blob URL (mutable but human-readable).</li>
   *   <li>fallback → bare repoUrl.</li>
   * </ul>
   */
  private String buildEntityId(GitReference gr, String sha) {
    String base = gr.getRepoUrl().replaceAll("/+$", "");
    if (sha != null && gr.getPath() != null && !gr.getPath().isBlank()) {
      return base + "/-/blob/" + sha + "/" + gr.getPath().replaceAll("^/+", "");
    }
    if (sha != null) {
      return base + "/-/commit/" + sha;
    }
    if (gr.getRef() != null && !gr.getRef().isBlank() && gr.getPath() != null && !gr.getPath().isBlank()) {
      return base + "/-/blob/" + gr.getRef() + "/" + gr.getPath().replaceAll("^/+", "");
    }
    return base;
  }
}
