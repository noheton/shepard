package de.dlr.shepard.common.neo4j.entities;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.Relationship;

@Data
@ToString(callSuper = true)
@NoArgsConstructor
public class BasicContainer extends BasicEntity implements HasPermissions {

  /**
   * Lifecycle status string. Nullable; null on legacy nodes means "not yet set".
   *
   * <p>Allowed values: {@code DRAFT}, {@code IN_REVIEW}, {@code READY},
   * {@code PUBLISHED}, {@code ARCHIVED}. Mirrors the same vocabulary on
   * {@link de.dlr.shepard.common.neo4j.entities.AbstractDataObject}.
   *
   * <p>Additive nullable field — no Neo4j migration needed. Pre-existing
   * {@code :*Container} nodes without this property are read as {@code null}.
   * {@code ARCHIVED} was added in #27-ARCHIVED-01; transition guards are
   * deferred to #27-ARCHIVED-02.
   */
  private String status;

  @ToString.Exclude
  @Relationship(type = Constants.HAS_PERMISSIONS)
  private Permissions permissions;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public BasicContainer(long id) {
    super(id);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + HasId.hashcodeHelper(permissions);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof BasicContainer)) return false;
    BasicContainer other = (BasicContainer) obj;
    return HasId.equalsHelper(permissions, other.permissions);
  }
}
