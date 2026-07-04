package de.dlr.shepard.auth.permission.io;

import com.fasterxml.jackson.annotation.JsonProperty;
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

  @Schema(readOnly = true, required = true, deprecated = true,
      description = "DEPRECATED (APISIMP-CONTAINERS-PERMS-IO-NUMERIC): internal Neo4j node ID. " +
        "Use the container's appId for all v2 operations; this field will be removed in a future sweep.")
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
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

  @Schema(deprecated = true,
      description = "DEPRECATED (APISIMP-CONTAINERS-PERMS-IO-NUMERIC): numeric Neo4j OGM group IDs. " +
        "Use readerGroupAppIds (UUID v7) to set reader groups; this field is output-only.")
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private long[] readerGroupIds = {};

  @Schema(deprecated = true,
      description = "DEPRECATED (APISIMP-CONTAINERS-PERMS-IO-NUMERIC): numeric Neo4j OGM group IDs. " +
        "Use writerGroupAppIds (UUID v7) to set writer groups; this field is output-only.")
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private long[] writerGroupIds = {};

  @Schema(
    nullable = true,
    description = "Application identifiers (UUID v7) of reader groups. Use in PATCH body to set reader groups."
  )
  private String[] readerGroupAppIds = {};

  @Schema(
    nullable = true,
    description = "Application identifiers (UUID v7) of writer groups. Use in PATCH body to set writer groups."
  )
  private String[] writerGroupAppIds = {};

  @NotNull
  @Schema(required = true)
  private String[] manager;

  public PermissionsIO(Permissions permissions) {
    // TODO: This could be multiple entities post versioning
    this.entityId = permissions.getEntities().getFirst().getNumericId();
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
    this.readerGroupAppIds = permissions.getReaderGroups().stream()
      .map(UserGroup::getAppId)
      .toArray(String[]::new);
    this.writerGroupAppIds = permissions.getWriterGroups().stream()
      .map(UserGroup::getAppId)
      .toArray(String[]::new);
    this.manager = permissions.getManager().stream().map(User::getUsername).toArray(String[]::new);
  }
}
