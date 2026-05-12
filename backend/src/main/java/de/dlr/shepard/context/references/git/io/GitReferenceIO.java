package de.dlr.shepard.context.references.git.io;

import de.dlr.shepard.context.references.git.entities.GitReference;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Wire shape for {@code /v2/data-objects/{appId}/git-references}
 * per {@code aidocs/38 §3}. v1 (G1a) exposes the **mode (a)** fields
 * — {@code repoUrl} / {@code ref} / {@code path}; mode-(b)/(c)
 * resolution fields are read-only and surface as null in v1.
 */
@Data
@NoArgsConstructor
@Schema(name = "GitReference")
public class GitReferenceIO {

  @Schema(readOnly = true, nullable = true, description = "Application identifier (UUID v7).")
  private String appId;

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
    this.repoUrl = src.getRepoUrl();
    this.ref = src.getRef();
    this.path = src.getPath();
    this.sha = src.getSha();
    this.resolvedSha = src.getResolvedSha();
    this.resolvedAtMillis = src.getResolvedAtMillis();
  }
}
