package de.dlr.shepard.auth.apikey.entities;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.neo4j.entities.HasCreationDate;
import de.dlr.shepard.common.neo4j.entities.Named;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
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
public class ApiKey implements HasId, Named, HasCreationDate {

  @Id
  @GeneratedValue(strategy = UuidStrategy.class)
  @Convert(UuidStringConverter.class)
  private UUID uid;

  private String name;

  @DateLong
  private Date createdAt;

  private String jws;

  @ToString.Exclude
  @Relationship(type = Constants.BELONGS_TO)
  private User belongsTo;

  public ApiKey(String name, Date createdAt, User belongsTo) {
    this.name = name;
    this.createdAt = createdAt;
    this.belongsTo = belongsTo;
  }

  /**
   * For testing purposes only
   *
   * @param uid identifies the entity
   */
  public ApiKey(UUID uid) {
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
    result = prime * result + Objects.hash(createdAt, jws, name, uid);
    result = prime * result + HasId.hashcodeHelper(belongsTo);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof ApiKey)) return false;
    ApiKey other = (ApiKey) obj;
    return (
      HasId.equalsHelper(belongsTo, other.belongsTo) &&
      Objects.equals(createdAt, other.createdAt) &&
      Objects.equals(jws, other.jws) &&
      Objects.equals(name, other.name) &&
      Objects.equals(uid, other.uid)
    );
  }
}
