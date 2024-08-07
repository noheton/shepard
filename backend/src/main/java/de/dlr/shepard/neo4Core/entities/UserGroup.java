package de.dlr.shepard.neo4Core.entities;

import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.HasId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
@Data
@ToString(callSuper = true)
@NoArgsConstructor
public class UserGroup extends BasicEntity {

  @Relationship(type = Constants.IS_IN_GROUP, direction = Relationship.INCOMING)
  private List<User> users = new ArrayList<>();

  @ToString.Exclude
  @Relationship(type = Constants.HAS_PERMISSIONS)
  private Permissions permissions;

  public UserGroup(long id) {
    super(id);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(users);
    result = prime * result + HasId.hashcodeHelper(permissions);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof UserGroup)) return false;
    UserGroup other = (UserGroup) obj;
    return Objects.equals(users, other.users) && HasId.equalsHelper(permissions, other.permissions);
  }
}
