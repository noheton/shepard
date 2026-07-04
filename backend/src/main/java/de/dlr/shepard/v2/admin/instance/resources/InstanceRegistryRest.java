package de.dlr.shepard.v2.admin.instance.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.instance.io.InstanceRegistryIO;
import de.dlr.shepard.v2.admin.instance.io.InstanceRegistryPatchIO;
import de.dlr.shepard.v2.admin.instance.io.RegisteredInstanceIO;
import de.dlr.shepard.v2.admin.instance.services.InstanceRegistryService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
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
 * APISIMP-INSTANCE-REGISTRY-BESPOKE — admin write surface for the instance registry.
 *
 * <p>Exposes only {@code PATCH /v2/admin/instances} (operator-only). The public
 * read has been moved to
 * {@link de.dlr.shepard.v2.instance.InstanceRegistryPublicRest} at
 * {@code GET /v2/instance/registry}, consistent with the established pattern that
 * {@code /v2/admin/} paths are operator-only and public reads live under
 * {@code /v2/instance/}.
 *
 * <p>RFC 7396 PATCH semantics: {@code instances} is an atomic array field.
 * Sending {@code {"instances": []}} replaces the list with empty. Omitting
 * {@code instances} leaves the current list unchanged.
 *
 * @see InstanceRegistryService
 * @see de.dlr.shepard.v2.instance.InstanceRegistryPublicRest
 */
@Path("/v2/admin/instances")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "Admin")
public class InstanceRegistryRest {

  @Inject
  InstanceRegistryService service;

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
  @APIResponse(responseCode = "401", description = "Request is not authenticated.")
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
