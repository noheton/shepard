package de.dlr.shepard.v2.admin.resources;

import de.dlr.shepard.common.configuration.feature.runtime.FeatureToggleRegistry;
import de.dlr.shepard.common.exceptions.ApiError;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.io.FeatureToggleIO;
import de.dlr.shepard.v2.admin.io.PatchFeatureToggleIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/admin/features")
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class AdminFeaturesRest {

  @Inject
  FeatureToggleRegistry registry;

  @GET
  @Operation(
    summary = "List runtime feature toggles.",
    description = "Returns all registered feature toggles with their current enabled state. " +
    "Changes made via PATCH take effect immediately in the running JVM but are not persisted " +
    "across restarts — the config-property value is restored on next startup."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current state of all feature toggles.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = FeatureToggleIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response list() {
    List<FeatureToggleIO> result = registry.list()
      .stream()
      .map(e -> new FeatureToggleIO(e.getName(), e.isEnabled(), e.getDescription(), e.getSource()))
      .toList();
    return Response.ok(result).build();
  }

  @PATCH
  @Path("/{name}")
  @Operation(
    summary = "Toggle a runtime feature flag.",
    description = "Sets the enabled state of the named feature toggle for the lifetime of the " +
    "current JVM process. The change does not survive a restart."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated state of the toggle.",
    content = @Content(schema = @Schema(implementation = FeatureToggleIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @APIResponse(responseCode = "404", description = "No toggle registered under that name.")
  public Response patch(
    @PathParam("name") String name,
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = PatchFeatureToggleIO.class))) @Valid PatchFeatureToggleIO body
  ) {
    return registry.get(name).map(entry -> {
      entry.setEnabled(body.getEnabled());
      return Response.ok(new FeatureToggleIO(entry.getName(), entry.isEnabled(), entry.getDescription(), entry.getSource())).build();
    }).orElseGet(() ->
      Response.status(Status.NOT_FOUND)
        .entity(new ApiError(Status.NOT_FOUND.getStatusCode(), "NotFound", "No feature toggle registered with name '" + name + "'"))
        .build()
    );
  }
}
