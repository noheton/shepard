package de.dlr.shepard.auth.permission.io;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.common.util.PermissionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "Permissions")
public class PermissionsIO {

  @Schema(readOnly = true, required = true)
  private long entityId;

  private String owner;

  @Schema(required = true)
  private PermissionType permissionType;

  @NotNull
  @Schema(required = true)
  private String[] reader;

  @NotNull
  @Schema(required = true)
  private String[] writer;

  private long[] readerGroupIds = {};

  private long[] writerGroupIds = {};

  @NotNull
  @Schema(required = true)
  private String[] manager;

  public PermissionsIO(Permissions permissions) {
    // TODO: This could be multiple entities post versioning
    this.entityId = permissions.getEntities().get(0).getNumericId();
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
