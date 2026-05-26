package de.dlr.shepard.v2.admin.semantic.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.context.semantic.entities.OntologyGitSource;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TPL5 — wire shape for a single
 * {@link de.dlr.shepard.context.semantic.entities.OntologyGitSource}.
 *
 * <p>Used as both the request body for
 * {@code POST /v2/admin/semantic/git-sources} (create) and the
 * response body for all git-source endpoints. Fields not supplied
 * on create are defaulted at the service layer ({@code branch →
 * "main"}, {@code pathPattern → "*.ttl"}).
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "An ontology Git source — a repository that Shepard polls for ontology files.")
public class OntologyGitSourceIO {

  @Schema(description = "Stable application-level identifier (UUID v7). Read-only; set on create.", readOnly = true)
  private String appId;

  @Schema(description = "Human-readable label for this source.", required = true, example = "InfAI m4i ontologies")
  private String name;

  @Schema(
    description = "Full git clone URL. Accepted: https:// or git@host:path.",
    required = true,
    example = "https://github.com/InfAI-Leipzig/m4i-ontologies"
  )
  private String repoUrl;

  @Schema(description = "Branch to clone. Defaults to 'main' when not supplied.", example = "main")
  private String branch;

  @Schema(
    description = "Glob pattern for ontology files within the cloned repo. " +
    "A bare pattern like '*.ttl' is matched at any depth; " +
    "a path-qualified one like 'ontologies/*.owl' is relative to the repo root.",
    example = "*.ttl"
  )
  private String pathPattern;

  @Schema(
    description = "appId of the SemanticRepository to ingest into. " +
    "Currently informational — all uploads target the internal n10s store.",
    nullable = true
  )
  private String targetRepoAppId;

  @Schema(description = "When false the nightly scheduler skips this source. Default true.")
  private Boolean enabled;

  @Schema(description = "Epoch-ms of the last completed ingest run. Null if never run.", readOnly = true)
  private Long lastIngestedAt;

  @Schema(description = "Last ingest status: OK, ERROR, or PENDING. Null if never run.", readOnly = true)
  private String lastStatus;

  @Schema(description = "Last error message. Null when lastStatus is OK.", readOnly = true)
  private String lastError;

  @Schema(description = "Epoch-ms when this record was created.", readOnly = true)
  private Long createdAt;

  @Schema(description = "Username of the admin who created this record.", readOnly = true)
  private String createdBy;

  /** Project a Neo4j entity to the wire shape. */
  public static OntologyGitSourceIO from(OntologyGitSource entity) {
    if (entity == null) return null;
    OntologyGitSourceIO io = new OntologyGitSourceIO();
    io.setAppId(entity.getAppId());
    io.setName(entity.getName());
    io.setRepoUrl(entity.getRepoUrl());
    io.setBranch(entity.getBranch());
    io.setPathPattern(entity.getPathPattern());
    io.setTargetRepoAppId(entity.getTargetRepoAppId());
    io.setEnabled(entity.isEnabled());
    io.setLastIngestedAt(entity.getLastIngestedAt());
    io.setLastStatus(entity.getLastStatus());
    io.setLastError(entity.getLastError());
    io.setCreatedAt(entity.getCreatedAt());
    io.setCreatedBy(entity.getCreatedBy());
    return io;
  }
}
