package de.dlr.shepard.v2.admin.instance.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.instance.io.InstanceRegistryIO;
import de.dlr.shepard.v2.admin.instance.io.InstanceRegistryPatchIO;
import de.dlr.shepard.v2.admin.instance.io.RegisteredInstanceIO;
import de.dlr.shepard.v2.admin.instance.services.InstanceRegistryService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * FE-PROV-INSTANCE-REGISTRY — admin REST surface for the instance registry singleton.
 *
 * <p>Lives under {@code /v2/admin/instances}. The GET endpoint is intentionally
 * public (no JWT required) — it is used by the frontend badge hover to resolve
 * an instance ID to a friendly name before the user authenticates. The same
 * posture as {@code /v2/instance/capabilities}. Only the PATCH endpoint requires
 * the instance-admin role.
 *
 * <p><b>Auth design note.</b> {@code @RolesAllowed} is placed on PATCH only, NOT
 * at the class level. Placing it at the class level would cause the JAX-RS role
 * check to block GET even when {@code JWTFilter} bypasses the JWT check — the
 * auth layers are independent. GET carries {@code @PermitAll} explicitly to
 * signal the intentional posture.
 *
 * <p>Path {@code /v2/admin/instances} is registered in
 * {@link de.dlr.shepard.common.filters.PublicEndpointRegistry#PUBLIC_PATHS}
 * so {@link de.dlr.shepard.common.filters.JWTFilter} skips the JWT check for
 * this exact path.
 *
 * <p>RFC 7396 PATCH semantics: {@code instances} is an atomic array field.
 * Sending {@code {"instances": []}} replaces the list with empty. Omitting
 * {@code instances} leaves the current list unchanged.
 *
 * @see InstanceRegistryService
 */
@Path("/v2/admin/instances")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "Admin")
public class InstanceRegistryRest {

  @Inject
  InstanceRegistryService service;

  @GET
  @PermitAll
  @Operation(
    operationId = "getRegistry",
    summary = "Read the current :InstanceRegistry singleton.",
    description = "Returns the list of registered peer Shepard instances. " +
    "Public endpoint — no JWT required (same posture as /v2/instance/capabilities). " +
    "Used by the frontend badge hover to resolve an instance ID to a friendly name " +
    "before the user authenticates. An empty 'instances' list means no peer " +
    "instances have been registered yet."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current instance registry.",
    content = @Content(schema = @Schema(implementation = InstanceRegistryIO.class))
  )
  public Response getRegistry() {
    InstanceRegistryIO registry = service.current();
    return Response.ok(registry).build();
  }

  @PATCH
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  @Operation(
    operationId = "patchRegistry",
    summary = "RFC 7396 merge-patch the :InstanceRegistry singleton.",
    description = "Replaces the 'instances' list atomically (RFC 7396 array semantics). " +
    "Absent = leave current list alone; null = clear the list (set to []); " +
    "present array (even []) = full replace. No element-level merge is performed. " +
    "PROV1a's ProvenanceCaptureFilter captures this PATCH as an :Activity row. " +
    "Requires the instance-admin role."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated registry returned in the same shape as GET.",
    content = @Content(schema = @Schema(implementation = InstanceRegistryIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patchRegistry(InstanceRegistryPatchIO body) {
    InstanceRegistryPatchIO patch = body == null ? new InstanceRegistryPatchIO() : body;

    InstanceRegistryIO current = service.current();
    List<RegisteredInstanceIO> effectiveInstances =
      patch.isInstancesTouched() ? patch.getInstances() : current.instances();

    // Treat explicit null as "clear" (empty list).
    if (effectiveInstances == null) {
      effectiveInstances = Collections.emptyList();
    }

    InstanceRegistryIO saved = service.patch(effectiveInstances);
    return Response.ok(saved).build();
  }
}
