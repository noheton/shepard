package de.dlr.shepard.auth.users.io;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.common.util.HasId;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "UserGroup")
public class UserGroupIO extends BasicEntityIO {

  @NotNull
  @Schema(required = true)
  private String[] usernames;

  public UserGroupIO(UserGroup userGroup) {
    super(userGroup);
    this.usernames = userGroup.getUsers().stream().map(User::getUsername).toArray(String[]::new);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (!super.equals(o)) return false;
    if (this.getClass() != o.getClass()) return false;
    UserGroupIO other = (UserGroupIO) o;
    return (HasId.areEqualSets(usernames, other.usernames));
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + HasId.hashcodeHelper(usernames);
    return result;
  }
}
