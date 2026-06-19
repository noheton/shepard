package de.dlr.shepard.v2.users.resources;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.users.endpoints.UserGroupAttributes;
import de.dlr.shepard.auth.users.io.UserGroupIO;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.v2.references.util.JsonNodeMaps;
import de.dlr.shepard.v2.users.io.UserGroupV2IO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.stream.StreamSupport;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * V2-SWEEP-002 — appId-keyed user-group CRUD at {@code /v2/user-groups}.
 *
 * <p>Delegates to the existing {@link UserGroupService}. The appId-keyed lookup
 * methods ({@code getUserGroupByAppId}, {@code patchUserGroupByAppId},
 * {@code deleteUserGroupByAppId}) were added to the service in V2-SWEEP-002.
 *
 * <p>The upstream {@code /shepard/api/userGroups} surface is unchanged
 * (zero wire-shape modification on the frozen byte-compat surface).
 *
 * <p>Frontend migration of the 11 {@code useShepardApi(UserGroupApi)} call
 * sites is tracked in V2-SWEEP-002-2.
 */
@Path("/v2/user-groups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed("authenticated")
@Tag(name = "UserGroups")
public class UserGroupV2Rest {

  @Inject
  UserGroupService service;

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }

  @GET
  @Operation(
    summary = "List all user groups the caller can read.",
    description = "Returns user groups the caller has at least Read permission on. Supports pagination and ordering."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of user groups.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = UserGroupV2IO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @Parameter(name = Constants.QP_PAGE, description = "Zero-based page index. Both page and pageSize must be provided to enable pagination; if either is omitted all results are returned.")
  @Parameter(name = "pageSize", description = "Page size (number of groups per page). Both page and pageSize must be provided to enable pagination; if either is omitted all results are returned.")
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE, description = "Sort attribute. Accepted values: name, createdAt, updatedAt. Default: insertion order (no guaranteed ordering when omitted).")
  @Parameter(name = Constants.QP_ORDER_DESC, description = "Sort direction. true = descending; false or omitted = ascending.")
  public Response listUserGroups(
    @QueryParam(Constants.QP_PAGE) @PositiveOrZero Integer page,
    @QueryParam("pageSize") @PositiveOrZero Integer pageSize,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) UserGroupAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    var params = new QueryParamHelper();
    if (page != null && pageSize != null) params = params.withPageAndSize(page, pageSize);
    if (orderBy != null) params = params.withOrderByAttribute(orderBy, orderDesc);
    List<UserGroupV2IO> result = service.getAllUserGroups(params).stream()
      .map(UserGroupV2IO::new)
      .toList();
    return Response.ok(result).build();
  }

  @GET
  @Path("/{appId}")
  @Operation(operationId = "getUserGroupV2", summary = "Get a user group by appId.")
  @APIResponse(
    responseCode = "200",
    description = "The user group.",
    content = @Content(schema = @Schema(implementation = UserGroupV2IO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "User group not found.")
  @Parameter(name = "appId", description = "UUID v7 application identifier.")
  public Response getUserGroup(@PathParam("appId") String appId) {
    return Response.ok(new UserGroupV2IO(service.getUserGroupByAppId(appId))).build();
  }

  @POST
  @Operation(operationId = "createUserGroupV2", summary = "Create a user group.")
  @APIResponse(
    responseCode = "201",
    description = "User group created.",
    content = @Content(schema = @Schema(implementation = UserGroupV2IO.class))
  )
  @APIResponse(responseCode = "400", description = "Invalid request body.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response createUserGroup(
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = UserGroupV2IO.class)))
    @Valid UserGroupV2IO body
  ) {
    var ioForService = new UserGroupIO();
    ioForService.setName(body.getName());
    ioForService.setUsernames(
      body.getUsernames() != null ? body.getUsernames().toArray(new String[0]) : new String[0]
    );
    var created = service.createUserGroup(ioForService);
    URI location = URI.create("/v2/user-groups/" + created.getAppId());
    return Response.created(location).entity(new UserGroupV2IO(created)).build();
  }

  @PATCH
  @Path("/{appId}")
  @Operation(
    summary = "Partially update a user group (RFC 7396 merge-patch).",
    description = "Fields present in the body replace the current value; absent fields are preserved. " +
    "Mutable fields: `name` (string), `usernames` (array of strings)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated user group.",
    content = @Content(schema = @Schema(implementation = UserGroupV2IO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing or invalid patch body.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "User group not found.")
  @Parameter(name = "appId", description = "UUID v7 application identifier.")
  public Response patchUserGroup(@PathParam("appId") String appId, JsonNode body) {
    if (body == null || body.isNull()) {
      return problem("/problems/user-groups.bad-request", "Bad Request", Response.Status.BAD_REQUEST,
          "patch body must not be null");
    }
    String newName = body.has("name") ? body.get("name").textValue() : null;
    List<String> newUsernames = null;
    if (body.has("usernames") && body.get("usernames").isArray()) {
      newUsernames = StreamSupport.stream(body.get("usernames").spliterator(), false)
        .map(JsonNode::textValue)
        .toList();
    }
    return Response.ok(new UserGroupV2IO(service.patchUserGroupByAppId(appId, newName, newUsernames))).build();
  }

  @DELETE
  @Path("/{appId}")
  @Operation(operationId = "deleteUserGroupV2", summary = "Delete a user group.")
  @APIResponse(responseCode = "204", description = "User group deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "User group not found.")
  @Parameter(name = "appId", description = "UUID v7 application identifier.")
  public Response deleteUserGroup(@PathParam("appId") String appId) {
    service.deleteUserGroupByAppId(appId);
    return Response.noContent().build();
  }

  // ─── permissions + roles (V2-SWEEP-002-PERMISSIONS) ───────────────────

  @GET
  @Path("/{appId}/roles")
  @Operation(
    operationId = "getUserGroupRolesV2",
    summary = "Get the caller's roles on a user group.",
    description = "Returns the caller's role flags (owner / manager / writer / reader) for the user group."
  )
  @APIResponse(
    responseCode = "200",
    description = "Caller's roles.",
    content = @Content(schema = @Schema(implementation = Roles.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "User group not found.")
  @Parameter(name = "appId", description = "UUID v7 application identifier.")
  public Response getUserGroupRoles(@PathParam("appId") String appId) {
    var group = service.getUserGroupByAppId(appId);
    return Response.ok(service.getUserGroupRoles(group.getId())).build();
  }

  @GET
  @Path("/{appId}/permissions")
  @Operation(
    operationId = "getUserGroupPermissionsV2",
    summary = "Get permissions for a user group.",
    description = "Returns the full permissions for the user group. Requires Manage access."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current permissions.",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Manage on the user group.")
  @APIResponse(responseCode = "404", description = "User group not found.")
  @Parameter(name = "appId", description = "UUID v7 application identifier.")
  public Response getUserGroupPermissions(@PathParam("appId") String appId) {
    var group = service.getUserGroupByAppId(appId);
    return Response.ok(new PermissionsIO(service.getUserGroupPermissions(group.getId()))).build();
  }

  @PATCH
  @Path("/{appId}/permissions")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    summary = "Merge-patch permissions for a user group (RFC 7396).",
    description =
      "RFC 7396 merge-patch the permissions for the user group at `appId`. " +
      "Only fields present in the body are applied; absent fields are left unchanged. " +
      "Mutable fields: `permissionType`, `owner`, `reader`, `writer`, `manager`, " +
      "`readerGroupIds`, `writerGroupIds`. Requires Manage access."
  )
  @APIResponse(
    responseCode = "200",
    description = "Post-patch permissions.",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(responseCode = "400", description = "Body is not a JSON object.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Manage on the user group.")
  @APIResponse(responseCode = "404", description = "User group not found.")
  @Parameter(name = "appId", description = "UUID v7 application identifier.")
  public Response patchUserGroupPermissions(
    @PathParam("appId") String appId,
    @RequestBody(required = true, content = @Content(mediaType = "application/merge-patch+json")) JsonNode body
  ) {
    if (body == null || !body.isObject()) {
      return problem("/problems/user-groups.bad-request", "Bad Request", Response.Status.BAD_REQUEST,
          "PATCH body must be a JSON object");
    }
    var group = service.getUserGroupByAppId(appId);
    var current = new PermissionsIO(service.getUserGroupPermissions(group.getId()));
    var patch = JsonNodeMaps.toMap(body);
    if (patch.containsKey("permissionType") && patch.get("permissionType") instanceof String ptStr) {
      current.setPermissionType(de.dlr.shepard.common.util.PermissionType.valueOf(ptStr));
    }
    if (patch.containsKey("owner") && patch.get("owner") instanceof String ownerStr) {
      current.setOwner(ownerStr);
    }
    if (patch.containsKey("reader") && patch.get("reader") instanceof java.util.List<?> readerList) {
      current.setReader(readerList.stream().map(Object::toString).toArray(String[]::new));
    }
    if (patch.containsKey("writer") && patch.get("writer") instanceof java.util.List<?> writerList) {
      current.setWriter(writerList.stream().map(Object::toString).toArray(String[]::new));
    }
    if (patch.containsKey("manager") && patch.get("manager") instanceof java.util.List<?> managerList) {
      current.setManager(managerList.stream().map(Object::toString).toArray(String[]::new));
    }
    if (patch.containsKey("readerGroupIds") && patch.get("readerGroupIds") instanceof java.util.List<?> rgl) {
      current.setReaderGroupIds(rgl.stream().mapToLong(v -> Long.parseLong(v.toString())).toArray());
    }
    if (patch.containsKey("writerGroupIds") && patch.get("writerGroupIds") instanceof java.util.List<?> wgl) {
      current.setWriterGroupIds(wgl.stream().mapToLong(v -> Long.parseLong(v.toString())).toArray());
    }
    var updated = service.updateUserGroupPermissions(current, group.getId());
    return Response.ok(new PermissionsIO(updated)).build();
  }
}
