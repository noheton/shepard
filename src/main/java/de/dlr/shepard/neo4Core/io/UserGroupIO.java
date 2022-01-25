package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(name = "UserGroup")
public class UserGroupIO {

	@NotNull
	private String name;

	@NotNull
	private String[] usernames;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private Long id;

	public UserGroupIO(UserGroup userGroup) {
		this.name = userGroup.getName();
		this.usernames = userGroup.getUsers().stream().map(User::getUsername).toArray(String[]::new);
		this.id = userGroup.getId();
	}

}
