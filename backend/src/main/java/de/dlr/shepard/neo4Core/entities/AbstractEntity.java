package de.dlr.shepard.neo4Core.entities;

import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.HasId;
import java.util.Date;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.DateLong;

/*
 * Abstract base class for most entities that
 * - have an id
 * - have a deleted flag
 * - have meta data for create and update
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public abstract class AbstractEntity implements HasId {

  @Id
  @GeneratedValue
  protected Long id;

  @Index
  protected boolean deleted = false;

  @DateLong
  protected Date createdAt;

  @ToString.Exclude
  @Relationship(type = Constants.CREATED_BY)
  protected User createdBy;

  @DateLong
  protected Date updatedAt;

  @ToString.Exclude
  @Relationship(type = Constants.UPDATED_BY)
  protected User updatedBy;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public AbstractEntity(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.getClass().getName(), id);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (this.getClass() != o.getClass()) return false;
    AbstractEntity entity = (AbstractEntity) o;
    return (
      id.equals(entity.id) &&
      deleted == entity.deleted &&
      createdAt.equals(entity.createdAt) &&
      createdBy.equals(entity.createdBy) &&
      updatedAt.equals(entity.updatedAt) &&
      updatedBy.equals(entity.updatedBy)
    );
  }

  @Override
  public String getUniqueId() {
    return id.toString();
  }
}
