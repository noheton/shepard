package de.dlr.shepard.auth.role.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * A role tier — the {@code (:Role {name})} node referenced by the
 * {@code (:User)-[:HAS_ROLE]->(:Role)} relationship. v1 carries a
 * single row, {@code instance-admin}; future tiers (e.g. {@code viewer}
 * / {@code auditor}) parallel via additional rows of this same shape.
 *
 * <p>Designed in {@code aidocs/51 §3.1}. The relationship's properties
 * ({@code grantedBy} / {@code grantedAt}) live on the {@code :HAS_ROLE}
 * edge, not on this node — they're an audit trail per-grant, not
 * per-role.
 */
@NodeEntity
@Data
@NoArgsConstructor
public class Role implements HasId, HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Per L2a's seam, minted on
   * save by {@code GenericDAO#createOrUpdate}.
   */
  @Property("appId")
  private String appId;

  /**
   * Hyphenated identifier — e.g. {@code "instance-admin"}. The string
   * fed to {@code @RolesAllowed(...)} matches this property exactly.
   */
  @Index(unique = true)
  @Property("name")
  private String name;

  /**
   * Title-case human-facing label — e.g. {@code "Instance Admin"}.
   */
  @Property("displayName")
  private String displayName;

  public Role(String name, String displayName) {
    this.name = name;
    this.displayName = displayName;
  }

  /** For testing purposes only. */
  public Role(long id) {
    this.id = id;
  }

  @Override
  public String getUniqueId() {
    return name;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Role other)) return false;
    return Objects.equals(name, other.name);
  }
}
