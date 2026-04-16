package de.dlr.shepard.common.neo4j.entities;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.common.util.Neo4jLabels;
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
public abstract class AbstractEntity implements HasId, Deletable, HasProvenance {

  @Id
  @GeneratedValue
  protected Long id;

  @Index
  protected boolean deleted = false;

  @DateLong
  protected Date createdAt;

  @ToString.Exclude
  @Relationship(type = Neo4jLabels.CREATED_BY)
  protected User createdBy;

  @DateLong
  protected Date updatedAt;

  @ToString.Exclude
  @Relationship(type = Neo4jLabels.UPDATED_BY)
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
      Objects.equals(createdAt, entity.createdAt) &&
      Objects.equals(createdBy, entity.createdBy) &&
      Objects.equals(updatedAt, entity.updatedAt) &&
      Objects.equals(updatedBy, entity.updatedBy)
    );
  }

  @Override
  public String getUniqueId() {
    return id.toString();
  }
}
