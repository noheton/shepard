package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.neo4Core.io.RolesIO;
import de.dlr.shepard.neo4Core.io.UserGroupIO;
import de.dlr.shepard.neo4Core.orderBy.UserGroupAttributes;
import de.dlr.shepard.util.Constants;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

public interface UserGroupRest {
  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Get all usergroups")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = UserGroupIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getAllUserGroups(Integer page, Integer size, UserGroupAttributes orderAttribute, Boolean orderDesc);

  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Get usergroup")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = UserGroupIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getUserGroup(Long id);

  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Create a new usergroup")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = UserGroupIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response createUserGroup(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = UserGroupIO.class))
    ) @Valid UserGroupIO userGroup
  );

  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Update usergroup")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = UserGroupIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response updateUserGroup(
    Long id,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = UserGroupIO.class))
    ) @Valid UserGroupIO userGroup
  );

  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Delete usergroup")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteUserGroup(Long id);

  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Get permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getUserGroupPermissions(long userGroupId);

  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Edit permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response editUserGroupPermissions(
    long userGroupId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO permissions
  );

  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Get roles")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = RolesIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getUserGroupRoles(long userGroupId);
}
