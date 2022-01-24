package de.dlr.shepard.neo4Core.io;

import org.apache.commons.lang3.ArrayUtils;

import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.util.PermissionType;
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

	private PermissionType permissionType;

	@NotNull
	private String[] reader;

	@NotNull
	private String[] writer;

	private long[] readerGroupIds = new long[0];

	private long[] writerGroupIds = new long[0];

	@NotNull
	private String[] manager;

	public PermissionsIO(Permissions permissions) {
		this.entityId = permissions.getEntity().getId();
		this.permissionType = permissions.getPermissionType();
		this.owner = permissions.getOwner() != null ? permissions.getOwner().getUsername() : null;
		this.reader = permissions.getReader().stream().map(User::getUsername).toArray(String[]::new);
		this.writer = permissions.getWriter().stream().map(User::getUsername).toArray(String[]::new);
		this.readerGroupIds = ArrayUtils
				.toPrimitive(permissions.getReaderGroups().stream().map(UserGroup::getId).toArray(Long[]::new));
		this.writerGroupIds = ArrayUtils
				.toPrimitive(permissions.getWriterGroups().stream().map(UserGroup::getId).toArray(Long[]::new));
		this.manager = permissions.getManager().stream().map(User::getUsername).toArray(String[]::new);
	}

}
