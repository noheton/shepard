package de.dlr.shepard.auth.users.entities;

import de.dlr.shepard.common.neo4j.entities.AbstractEntity;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

/**
 * PROV-USER-MIRROR-ENDPOINT — a lightweight shadow node representing a user
 * from a <em>remote</em> Shepard instance for cross-instance PROV-O attribution.
 *
 * <p>When the v15 MFFD importer script runs on a source instance and forwards
 * {@code X-Source-User-*} HTTP headers to the destination, the destination
 * Shepard has no OIDC-federated identity for the source-side user. This node
 * provides a stable, local hook that the provenance attribution chain
 * ({@code prov:wasAssociatedWith}) can point to without creating a fully-fledged
 * {@link User} node that would pollute Keycloak / OIDC.
 *
 * <p><b>Uniqueness invariant:</b> at most one {@code :MirroredUser} node per
 * {@code (sourceInstance, sourceUsername)} pair — enforced by the DAO's
 * {@code MERGE} pattern and the index added in
 * {@code V74__Add_MirroredUser_appId_constraint.cypher}.
 *
 * <p><b>PROV-O chain:</b>
 * {@code :Activity -[prov:wasAssociatedWith]-> :MirroredUser
 *   -[DERIVED_FROM]-> :MirroredUser?} (optional chaining for re-mirroring hops).
 *
 * @see de.dlr.shepard.v2.admin.users.MirroredUserRest
 */
@NodeEntity("MirroredUser")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class MirroredUser extends AbstractEntity {

  /**
   * Base URL of the source Shepard instance (e.g. {@code https://cube3.dlr.de/shepard}).
   * Part of the uniqueness key — combined with {@link #sourceUsername}.
   */
  @Property("sourceInstance")
  private String sourceInstance;

  /**
   * Username as known on the source instance (e.g. the Keycloak {@code sub} or
   * the {@code preferred_username} claim). Part of the uniqueness key.
   */
  @Property("sourceUsername")
  private String sourceUsername;

  /**
   * Human-readable display name as resolved on the source side.
   * Nullable — may be absent when the source could not resolve it.
   */
  @Property("sourceDisplayName")
  private String sourceDisplayName;

  /**
   * Email address as known on the source side. Nullable — omitted when
   * the source-side JWT does not expose an email claim.
   */
  @Property("sourceEmail")
  private String sourceEmail;

  /**
   * Optional back-link to a parent {@link MirroredUser} — used when this
   * node was itself derived from another mirror (multi-hop cross-instance
   * provenance). {@code null} for root mirror nodes (the common case).
   *
   * <p>Exposed on the entity for future graph-traversal use; not surfaced
   * in the REST response shape (v1 of this endpoint does not need chaining).
   */
  @Relationship(type = "DERIVED_FROM")
  @ToString.Exclude
  private MirroredUser derivedFrom;

  /** Testing constructor — mirrors {@link AbstractEntity#AbstractEntity(long)}. */
  public MirroredUser(long id) {
    super(id);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MirroredUser other)) return false;
    return Objects.equals(sourceInstance, other.sourceInstance) &&
           Objects.equals(sourceUsername, other.sourceUsername);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceInstance, sourceUsername);
  }
}
