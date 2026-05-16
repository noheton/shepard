package de.dlr.shepard.context.references.git.io;

import de.dlr.shepard.context.references.git.entities.GitReference;
import de.dlr.shepard.context.references.git.entities.GitReferenceMode;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Wire shape for {@code /v2/data-objects/{appId}/git-references}
 * per {@code aidocs/38 §3}. G1a exposes the **mode (a)** fields
 * ({@code repoUrl} / {@code ref} / {@code path}); G1b adds {@code mode}
 * (writable) and lights up {@code resolvedSha} / {@code resolvedAtMillis}
 * (read-only) for tracked-artifact rows; G1c will populate {@code sha}.
 */
@Data
@NoArgsConstructor
@Schema(name = "GitReference")
public class GitReferenceIO {

  @Schema(readOnly = true, nullable = true, description = "Application identifier (UUID v7).")
  private String appId;

  @Schema(
    nullable = true,
    description = "Use-mode (aidocs/38 §2). LOOSE_LINK (default) renders as a clickable URL; " +
    "TRACKED_ARTIFACT enables the server-side inline preview via " +
    "GET /v2/data-objects/{do}/git-references/{appId}/preview; PINNED_SNAPSHOT is reserved for G1c."
  )
  private GitReferenceMode mode;

  @Schema(required = true, description = "Git repository URL, e.g. https://gitlab.dlr.de/group/repo.")
  private String repoUrl;

  @Schema(nullable = true, description = "Branch name, tag, or HEAD. Null = repository default.")
  private String ref;

  @Schema(nullable = true, description = "Path inside the repository. Null = repository root.")
  private String path;

  @Schema(
    readOnly = true,
    nullable = true,
    description = "Resolved commit SHA for mode-(c) pinned snapshots. Always null in mode (a) v1; populated by G1c."
  )
  private String sha;

  @Schema(
    readOnly = true,
    nullable = true,
    description = "SHA that fulfilled `ref` at the last fetch (mode (b)). Always null in v1; populated by G1b."
  )
  private String resolvedSha;

  @Schema(
    readOnly = true,
    nullable = true,
    description = "Epoch-millis when `resolvedSha` was captured. Null in mode (a) v1."
  )
  private Long resolvedAtMillis;

  public GitReferenceIO(GitReference src) {
    this.appId = src.getAppId();
    this.mode = src.getMode();
    this.repoUrl = src.getRepoUrl();
    this.ref = src.getRef();
    this.path = src.getPath();
    this.sha = src.getSha();
    this.resolvedSha = src.getResolvedSha();
    this.resolvedAtMillis = src.getResolvedAtMillis();
  }
}
