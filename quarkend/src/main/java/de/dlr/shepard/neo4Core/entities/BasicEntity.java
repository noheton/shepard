package de.dlr.shepard.neo4Core.entities;

import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.HasId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.DateLong;

@Data
@NoArgsConstructor
public class BasicEntity implements HasId {

  @Id
  @GeneratedValue
  private Long id;

  @Index
  private boolean deleted = false;

  @DateLong
  private Date createdAt;

  @ToString.Exclude
  @Relationship(type = Constants.CREATED_BY)
  private User createdBy;

  @DateLong
  private Date updatedAt;

  @ToString.Exclude
  @Relationship(type = Constants.UPDATED_BY)
  private User updatedBy;

  private String name;

  @ToString.Exclude
  @Relationship(type = Constants.HAS_ANNOTATION)
  private List<SemanticAnnotation> annotations = new ArrayList<>();

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public BasicEntity(long id) {
    this.id = id;
  }

  public void addAnnotation(SemanticAnnotation annotation) {
    annotations.add(annotation);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Objects.hash(id, deleted, createdAt, updatedAt, name);
    result = prime * result + HasId.hashcodeHelper(createdBy);
    result = prime * result + HasId.hashcodeHelper(updatedBy);
    result = prime * result + HasId.hashcodeHelper(annotations);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof BasicEntity)) return false;
    BasicEntity other = (BasicEntity) obj;
    return (
      Objects.equals(id, other.id) &&
      deleted == other.deleted &&
      Objects.equals(createdAt, other.createdAt) &&
      Objects.equals(updatedAt, other.updatedAt) &&
      HasId.equalsHelper(createdBy, other.createdBy) &&
      HasId.equalsHelper(updatedBy, other.updatedBy) &&
      Objects.equals(name, other.name) &&
      HasId.equalsHelper(annotations, other.annotations)
    );
  }

  @Override
  public String getUniqueId() {
    return id.toString();
  }
}
