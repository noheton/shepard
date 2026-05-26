package de.dlr.shepard.context.semantic.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * TPL5 — a Git repository that Shepard can poll for ontology files
 * ({@code .ttl}, {@code .owl}, {@code .rdf}, …).
 *
 * <p>Each enabled {@code :OntologyGitSource} is cloned (shallow,
 * {@code --depth=1}) nightly by
 * {@link de.dlr.shepard.context.semantic.services.OntologyGitIngestService}.
 * Files whose path matches {@code pathPattern} (glob, e.g.
 * {@code *.ttl} or {@code ontologies/*.owl}) are ingested as
 * {@link UserOntologyBundle} entries, namespaced under the source's
 * {@code appId} to avoid id collisions across sources.
 *
 * <p><b>Bundle-id derivation.</b> For each matching file the
 * bundle id is built as:
 * {@code git-<nameSlug>-<fileStem>} where {@code nameSlug} is the
 * first 16 chars of the source's {@code name} lowercased + sanitized
 * to {@code [a-z0-9_-]}, and {@code fileStem} is the filename
 * without extension (similarly sanitized).  On re-ingest the bundle
 * is deleted-and-re-uploaded so that the bytes stay current without
 * accumulating duplicate ids.
 *
 * <p><b>{@code targetRepoAppId}.</b> The current OntologyConfigService
 * upload pipeline only targets the internal n10s store, so this
 * field is informational metadata for now.  When multi-repo upload
 * lands it will be wired.
 *
 * <p><b>Security.</b>
 * {@link de.dlr.shepard.context.semantic.services.OntologyGitIngestService}
 * validates {@code repoUrl} (https:// or git@ prefix; no shell
 * metacharacters; no option-injection {@code --} prefix) and
 * {@code branch} (no metacharacters) before building the
 * {@link ProcessBuilder} command.
 *
 * @see de.dlr.shepard.context.semantic.services.OntologyGitIngestService
 * @see de.dlr.shepard.context.semantic.daos.OntologyGitSourceDAO
 */
@NodeEntity
@Data
@NoArgsConstructor
public class OntologyGitSource implements HasId, HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on save by
   * {@code GenericDAO#createOrUpdate} per L2a's seam.
   */
  @Property("appId")
  private String appId;

  /** Human-readable label for this git source — shown in the admin list. */
  @Property("name")
  private String name;

  /**
   * Full git clone URL.  Accepted shapes:
   * {@code https://...} or {@code git@host:path}.
   * Validated at service layer before use in {@link ProcessBuilder}.
   */
  @Property("repoUrl")
  private String repoUrl;

  /**
   * Branch to clone. Default {@code "main"}. Validated to contain
   * only {@code [A-Za-z0-9._/-]} (no shell metacharacters).
   */
  @Property("branch")
  private String branch;

  /**
   * Glob pattern for ontology files within the cloned repo,
   * e.g. {@code "*.ttl"} or {@code "ontologies/*.owl"}.
   * Evaluated via {@link java.nio.file.PathMatcher} glob semantics.
   */
  @Property("pathPattern")
  private String pathPattern;

  /**
   * The {@code appId} of the {@code :SemanticRepository} to ingest
   * into.  Currently informational — the upload pipeline always
   * targets the internal n10s store.  Will be wired when
   * per-repo upload lands.
   */
  @Property("targetRepoAppId")
  private String targetRepoAppId;

  /**
   * When {@code false} the nightly scheduler skips this source.
   * The manual-ingest endpoint still works regardless of this flag.
   */
  @Property("enabled")
  private boolean enabled = true;

  /** Epoch-ms of the last completed ingest run, or {@code null} if never run. */
  @Property("lastIngestedAt")
  private Long lastIngestedAt;

  /**
   * Last ingest status: {@code "OK"}, {@code "ERROR"}, or
   * {@code "PENDING"} (set at start of run, replaced on finish).
   */
  @Property("lastStatus")
  private String lastStatus;

  /**
   * Last error message, or {@code null} when {@code lastStatus == "OK"}.
   * Truncated to 4096 characters to guard against enormous git error output.
   */
  @Property("lastError")
  private String lastError;

  /** Epoch-ms when this source record was created. */
  @Property("createdAt")
  private Long createdAt;

  /** Username of the admin who created this record. */
  @Property("createdBy")
  private String createdBy;

  @Override
  public String getUniqueId() {
    return id == null ? null : id.toString();
  }
}
