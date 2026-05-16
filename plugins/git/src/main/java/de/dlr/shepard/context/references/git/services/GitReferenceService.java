package de.dlr.shepard.context.references.git.services;

import de.dlr.shepard.auth.users.services.GitCredentialService;
import de.dlr.shepard.context.references.git.adapters.FileResolution;
import de.dlr.shepard.context.references.git.adapters.GitAdapter;
import de.dlr.shepard.context.references.git.adapters.GitAdapterException;
import de.dlr.shepard.context.references.git.adapters.GitAdapterRegistry;
import de.dlr.shepard.context.references.git.adapters.ParsedRepoUrl;
import de.dlr.shepard.context.references.git.daos.GitReferenceDAO;
import de.dlr.shepard.context.references.git.entities.GitReference;
import de.dlr.shepard.context.references.git.entities.GitReferenceMode;
import de.dlr.shepard.context.references.git.io.GitArtifactPreviewIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Service layer for the G1b tracked-artifact preview flow. Orchestrates:
 *
 * <ol>
 *   <li>{@link GitReferenceDAO} lookup + mode discrimination.</li>
 *   <li>{@link GitCredentialService} host → PAT lookup.</li>
 *   <li>{@link GitAdapterRegistry} host → adapter dispatch.</li>
 *   <li>{@link GitArtifactCache} cached fetch.</li>
 *   <li>{@code shepard.git.preview.max-bytes} truncation.</li>
 * </ol>
 *
 * <p>Permission checks live in {@code GitReferenceRest} (the parent
 * DataObject's Read gate); this service trusts its caller and operates
 * on an already-loaded {@link GitReference}.
 *
 * <p>One operator-visible knob the service surfaces directly:
 * {@code shepard.git.preview.max-bytes}. Files larger than this are
 * returned as {@code contentTruncated=true, content=null} so the UI
 * can fall back to an "open in GitLab" link.
 */
@RequestScoped
public class GitReferenceService {

  @Inject
  GitCredentialService gitCredentialService;

  @Inject
  GitAdapterRegistry gitAdapterRegistry;

  @Inject
  GitArtifactCache gitArtifactCache;

  @ConfigProperty(name = "shepard.git.preview.max-bytes", defaultValue = "1048576")
  long maxBytes;

  /** Public constructor for CDI. */
  public GitReferenceService() {}

  /** Visible-for-test constructor — wires all the dependencies. */
  GitReferenceService(
    GitCredentialService gitCredentialService,
    GitAdapterRegistry gitAdapterRegistry,
    GitArtifactCache gitArtifactCache,
    long maxBytes
  ) {
    this.gitCredentialService = gitCredentialService;
    this.gitAdapterRegistry = gitAdapterRegistry;
    this.gitArtifactCache = gitArtifactCache;
    this.maxBytes = maxBytes;
  }

  /**
   * Builds the preview payload for a tracked-artifact reference.
   *
   * @param ref       the (already-loaded, already-permission-checked) reference.
   * @param username  the caller's username — used to look up their PAT and
   *                  partition the cache.
   */
  public GitArtifactPreviewIO previewArtifact(GitReference ref, String username) {
    GitArtifactPreviewIO out = new GitArtifactPreviewIO();
    if (ref == null) {
      out.setAvailable(false);
      out.setReason("not-found");
      return out;
    }
    if (ref.getMode() != GitReferenceMode.TRACKED_ARTIFACT) {
      out.setAvailable(false);
      out.setReason("not-tracked");
      return out;
    }
    if (ref.getRepoUrl() == null || ref.getRepoUrl().isBlank()) {
      out.setAvailable(false);
      out.setReason("not-tracked");
      return out;
    }
    if (ref.getRef() == null || ref.getRef().isBlank() || ref.getPath() == null || ref.getPath().isBlank()) {
      out.setAvailable(false);
      out.setReason("not-tracked");
      return out;
    }

    ParsedRepoUrl parsed;
    try {
      parsed = ParsedRepoUrl.parse(ref.getRepoUrl());
    } catch (GitAdapterException e) {
      out.setAvailable(false);
      out.setReason("invalid-repo-url");
      return out;
    }

    Optional<GitAdapter> adapterOpt = gitAdapterRegistry.findByHost(parsed.host());
    if (adapterOpt.isEmpty()) {
      out.setAvailable(false);
      out.setReason("unsupported-host");
      return out;
    }

    Optional<String> patOpt = gitCredentialService.findPatForHost(username, parsed.host());
    if (patOpt.isEmpty()) {
      out.setAvailable(false);
      out.setReason("no-credential");
      return out;
    }

    FileResolution resolution;
    try {
      resolution = gitArtifactCache.getFile(
        username,
        ref.getRepoUrl(),
        ref.getRef(),
        ref.getPath(),
        adapterOpt.get(),
        patOpt.get()
      );
    } catch (GitAdapterException e) {
      Log.debugf("Git adapter fetch failed: %s", e.getMessage());
      out.setAvailable(false);
      out.setReason("fetch-failed");
      return out;
    }

    out.setAvailable(true);
    out.setSha(resolution.sha());
    out.setMimeType(resolution.mimeType());
    out.setByteSize(resolution.byteSize());

    byte[] body = resolution.content();
    if (body == null || body.length > maxBytes) {
      out.setContent(null);
      out.setContentTruncated(true);
    } else {
      out.setContent(new String(body, StandardCharsets.UTF_8));
      out.setContentTruncated(false);
    }
    return out;
  }
}
