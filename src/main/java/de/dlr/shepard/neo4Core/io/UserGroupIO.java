package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "UserGroup")
public class UserGroupIO extends AbstractEntityWithNameIO {

	@NotNull
	private String[] usernames;

	public UserGroupIO(UserGroup userGroup) {
		super(userGroup);
		this.usernames = userGroup.getUsers().stream().map(User::getUsername).toArray(String[]::new);
	}

}
