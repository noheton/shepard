package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(name = "Permissions")
public class PermissionsIO {

	@Schema(accessMode = AccessMode.READ_ONLY)
	private long entityId;

	private String owner;

	@NotNull
	private String[] reader;

	@NotNull
	private String[] writer;

	@NotNull
	private String[] manager;

	public PermissionsIO(Permissions permissions) {
		this.entityId = permissions.getEntity().getId();
		this.owner = permissions.getOwner() != null ? permissions.getOwner().getUsername() : null;
		this.reader = permissions.getReader().stream().map(User::getUsername).toArray(String[]::new);
		this.writer = permissions.getWriter().stream().map(User::getUsername).toArray(String[]::new);
		this.manager = permissions.getManager().stream().map(User::getUsername).toArray(String[]::new);
	}

}
