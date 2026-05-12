package de.dlr.shepard.context.references.git.entities;

import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.NodeEntity;

/**
 * Git-repository reference attached to a {@link de.dlr.shepard.context.collection.entities.DataObject},
 * per {@code aidocs/38 Git Integration §3}. v1 (G1a) ships **mode (a)
 * loose link** only: stores a {@code repoUrl} (and optional {@code ref}
 * / {@code path}) without fetching or auth — clients render it as a
 * clickable link.
 *
 * <p>Modes (b) "tracked artifact" and (c) "pinned snapshot" extend
 * this entity with {@code sha}, {@code resolvedSha}, and
 * {@code resolvedAt}; those fields are scaffolded here so the
 * follow-on slices don't re-shape the node label.
 *
 * <p>Inherits {@code appId} (UUID v7) and the {@code :HAS_REFERENCE}
 * back-edge to {@code :DataObject} from {@link BasicReference}.
 */
@NodeEntity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GitReference extends BasicReference {

  /**
   * Git repository URL, e.g.
   * {@code https://gitlab.dlr.de/group/repo}. Required.
   */
  private String repoUrl;

  /**
   * Branch name, tag, or {@code HEAD}. Null = repository default.
   */
  private String ref;

  /**
   * Path inside the repository. Null = repository root.
   */
  private String path;

  /**
   * Resolved commit SHA (mode (c) pinned snapshot). Always null in
   * mode (a); set by the export pipeline (G1c) for reproducible
   * RO-Crate exports.
   */
  private String sha;

  /**
   * SHA that fulfilled {@link #ref} at the last fetch (mode (b)).
   * Always null in mode (a); set by the tracked-artifact reader (G1b).
   */
  private String resolvedSha;

  /**
   * Epoch-millis timestamp of when {@link #resolvedSha} was captured
   * (mode (b)). Long-vs-Instant: matches the codebase's existing
   * {@code @DateLong} convention for millisecond-precision Neo4j
   * properties without adding a converter import here.
   */
  private Long resolvedAtMillis;

  /**
   * For testing purposes only.
   *
   * @param id identifies the entity
   */
  public GitReference(long id) {
    super(id);
  }

  /**
   * Mode-(a) convenience constructor.
   */
  public GitReference(String repoUrl, String ref, String path) {
    this.repoUrl = repoUrl;
    this.ref = ref;
    this.path = path;
  }
}
