package de.dlr.shepard.auth.users.io;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
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
}
