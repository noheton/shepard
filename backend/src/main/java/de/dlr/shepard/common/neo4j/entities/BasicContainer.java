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

  @ToString.Exclude
  @Relationship(type = Constants.HAS_PERMISSIONS)
  private Permissions permissions;

  /**
   * #27-ARCHIVED — lifecycle status for the container, mirroring the
   * {@code status} field on {@link de.dlr.shepard.common.neo4j.entities.AbstractDataObject}.
   * Free-form String (no closed-enum guard at the entity layer; validated at
   * the IO/service layer via {@code StatusTransitionGuard} when written).
   *
   * <p>Valid values: {@code DRAFT}, {@code IN_REVIEW}, {@code READY},
   * {@code PUBLISHED}, {@code ARCHIVED}. Null is treated as effectively
   * {@code READY} by the {@link de.dlr.shepard.context.collection.services.ArchiveStateGuard} —
   * pre-feature rows are unblocked by default. The runtime archive check is
   * "if status equals ARCHIVED then 409"; everything else passes.
   *
   * <p>Additive nullable property — no Neo4j migration required for storage.
   * See {@code V97__NOOP_BasicContainer_status_additive.cypher} for the
   * documented additive change.
   */
  private String status;

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
    result = prime * result + java.util.Objects.hashCode(status);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof BasicContainer)) return false;
    BasicContainer other = (BasicContainer) obj;
    return HasId.equalsHelper(permissions, other.permissions)
      && java.util.Objects.equals(status, other.status);
  }
}
