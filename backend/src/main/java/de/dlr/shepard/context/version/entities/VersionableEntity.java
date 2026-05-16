package de.dlr.shepard.context.version.entities;

import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.common.util.Constants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class VersionableEntity extends BasicEntity {

  @Index
  private Long shepardId;

  @Relationship(type = Constants.HAS_VERSION)
  protected Version version;

  /**
   * Monotonically increasing write counter. Starts at 1 on create and
   * increments by 1 on every subsequent save via {@link
   * de.dlr.shepard.common.neo4j.daos.GenericDAO#createOrUpdate}. Used as the
   * prerequisite for optimistic-locking (V2b snapshots). Server-managed — clients
   * must not write this field.
   *
   * <p>Existing rows without the property are backfilled to 1 by
   * V36__Backfill_revision.cypher.
   *
   * <p>Getter/setter are declared explicitly (rather than relying solely on
   * Lombok's {@code @Data}) so that callers in sibling packages (e.g.
   * {@code GenericDAO}) can resolve the methods even when annotation-processing
   * order causes Lombok-generated accessors to be invisible during compilation.
   */
  private long revision = 1L;

  public long getRevision() {
    return revision;
  }

  public void setRevision(long revision) {
    this.revision = revision;
  }

  // Explicit accessor for shepardId so that callers in sibling packages resolve
  // the method even when Lombok APT cannot process this class in the same javac
  // pass (e.g. when other files in the module have compilation errors that prevent
  // a clean Lombok run).
  public Long getShepardId() {
    return shepardId;
  }

  public void setShepardId(Long shepardId) {
    this.shepardId = shepardId;
  }

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  protected VersionableEntity(long id) {
    super(id);
  }

  @Override
  public long getNumericId() {
    return getShepardId();
  }
}
