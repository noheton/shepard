package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.util.PermissionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "Permissions")
public class PermissionsIO {

  @Schema(readOnly = true)
  private long entityId;

  private String owner;

  private PermissionType permissionType;

  @NotNull
  private String[] reader;

  @NotNull
  private String[] writer;

  private long[] readerGroupIds = {};

  private long[] writerGroupIds = {};

  @NotNull
  private String[] manager;

  public PermissionsIO(Permissions permissions) {
    this.entityId = permissions.getEntity().getId();
    this.permissionType = permissions.getPermissionType();
    this.owner = permissions.getOwner() != null ? permissions.getOwner().getUsername() : null;
    this.reader = permissions.getReader().stream().map(User::getUsername).toArray(String[]::new);
    this.writer = permissions.getWriter().stream().map(User::getUsername).toArray(String[]::new);
    this.readerGroupIds = ArrayUtils.toPrimitive(
      permissions.getReaderGroups().stream().map(UserGroup::getId).toArray(Long[]::new)
    );
    this.writerGroupIds = ArrayUtils.toPrimitive(
      permissions.getWriterGroups().stream().map(UserGroup::getId).toArray(Long[]::new)
    );
    this.manager = permissions.getManager().stream().map(User::getUsername).toArray(String[]::new);
  }
}
