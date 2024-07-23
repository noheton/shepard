package de.dlr.shepard.neo4Core.entities;

import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.HasId;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.annotation.typeconversion.DateLong;
import org.neo4j.ogm.id.UuidStrategy;
import org.neo4j.ogm.typeconversion.UuidStringConverter;

@NodeEntity
@Data
@NoArgsConstructor
public class Version implements HasId {

  @Id
  @GeneratedValue(strategy = UuidStrategy.class)
  @Convert(UuidStringConverter.class)
  private UUID uid;

  private String name;

  private String description;

  @DateLong
  private Date createdAt;

  @ToString.Exclude
  @Relationship(type = Constants.CREATED_BY)
  private User createdBy;

  @Relationship(type = Constants.HAS_PREDECESSOR)
  private Version predecessor;

  public Version(String name, String description, Date createdAt, User createdBy, Version predecessor) {
    this.name = name;
    this.description = description;
    this.createdAt = createdAt;
    this.createdBy = createdBy;
    this.predecessor = predecessor;
  }

  public Version(String name, String description, Date createdAt, User createdBy) {
    this.name = name;
    this.description = description;
    this.createdAt = createdAt;
    this.createdBy = createdBy;
  }

  /**
   * For testing purposes only
   *
   * @param uid identifies the entity
   */
  public Version(UUID uid) {
    this.uid = uid;
  }

  @Override
  public String getUniqueId() {
    return uid.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Objects.hash(createdAt, uid, name, description);
    result = prime * result + HasId.hashcodeHelper(createdBy);
    result = prime * result + HasId.hashcodeHelper(predecessor);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Version)) return false;
    Version other = (Version) obj;
    return (
      Objects.equals(createdAt, other.createdAt) &&
      HasId.equalsHelper(createdBy, other.createdBy) &&
      Objects.equals(description, other.description) &&
      Objects.equals(uid, other.uid) &&
      HasId.equalsHelper(predecessor, other.predecessor) &&
      Objects.equals(name, other.name)
    );
  }
}
