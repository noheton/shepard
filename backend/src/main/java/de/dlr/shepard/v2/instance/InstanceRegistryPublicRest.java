package de.dlr.shepard.v2.instance;

import de.dlr.shepard.v2.admin.instance.io.InstanceRegistryIO;
import de.dlr.shepard.v2.admin.instance.services.InstanceRegistryService;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * APISIMP-INSTANCE-REGISTRY-BESPOKE — public read of the instance registry.
 *
 * <p>Sits at the non-admin path {@code /v2/instance/registry}, consistent with
 * the established pattern of public capability reads at {@code /v2/instance/*}
 * (see {@code InstanceCapabilitiesRest} at {@code /v2/instance/capabilities}).
 * A path under {@code /v2/admin/} conventionally implies operator-only access;
 * having a {@code @PermitAll} GET there was misleading.
 *
 * <p>The admin write surface remains at {@code PATCH /v2/admin/instances}
 * ({@link de.dlr.shepard.v2.admin.instance.resources.InstanceRegistryRest}).
 *
 * <p>Registered in
 * {@link de.dlr.shepard.common.filters.PublicEndpointRegistry#PUBLIC_PATHS}
 * so {@link de.dlr.shepard.common.filters.JWTFilter} skips the JWT check.
 * Only exposes operator-configured peer instance metadata (names, URLs) —
 * no entity payload, no PII.
 */
@Path("/v2/instance/registry")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "Instance")
public class InstanceRegistryPublicRest {

  @Inject
  InstanceRegistryService service;

  @GET
  @PermitAll
  @Operation(
    operationId = "getInstanceRegistry",
    summary = "Read the current :InstanceRegistry singleton.",
    description = "Returns the list of registered peer Shepard instances. " +
    "Public endpoint — no JWT required (same posture as /v2/instance/capabilities). " +
    "Used by the frontend badge hover to resolve an instance ID to a friendly name " +
    "before the user authenticates. An empty 'instances' list means no peer " +
    "instances have been registered yet. " +
    "The admin write surface lives at PATCH /v2/admin/instances."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current instance registry.",
    content = @Content(schema = @Schema(implementation = InstanceRegistryIO.class))
  )
  public Response getInstanceRegistry() {
    InstanceRegistryIO registry = service.current();
    return Response.ok(registry).build();
  }
}
