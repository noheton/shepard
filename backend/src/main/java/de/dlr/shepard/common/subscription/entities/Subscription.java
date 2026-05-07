package de.dlr.shepard.common.subscription.entities;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.neo4j.entities.Named;
import de.dlr.shepard.common.neo4j.entities.UserCreated;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.common.util.RequestMethod;
import java.util.Date;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.DateLong;

@NodeEntity
@Data
@NoArgsConstructor
public class Subscription implements HasId, HasAppId, UserCreated, Named {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7) — additive in L2a.
   */
  @Property("appId")
  private String appId;

  private String name;

  private String callbackURL;

  private String subscribedURL;

  @Index
  private RequestMethod requestMethod;

  @ToString.Exclude
  @Relationship(type = Constants.SUBSCRIBED_BY)
  private User createdBy;

  @DateLong
  private Date createdAt;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public Subscription(long id) {
    this.id = id;
  }

  @Override
  public String getUniqueId() {
    return id.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Objects.hash(callbackURL, createdAt, id, name, createdBy, requestMethod, subscribedURL);
    result = prime * result + HasId.hashcodeHelper(createdBy);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Subscription other)) return false;
    return (
      Objects.equals(callbackURL, other.callbackURL) &&
      Objects.equals(createdAt, other.createdAt) &&
      Objects.equals(id, other.id) &&
      Objects.equals(name, other.name) &&
      HasId.equalsHelper(createdBy, other.createdBy) &&
      requestMethod == other.requestMethod &&
      Objects.equals(subscribedURL, other.subscribedURL)
    );
  }
}
