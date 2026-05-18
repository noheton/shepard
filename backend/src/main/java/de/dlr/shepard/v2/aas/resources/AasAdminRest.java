package de.dlr.shepard.v2.aas.resources;

import de.dlr.shepard.aas.services.AasIdtaTemplateImportService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.template.services.TemplateBodyValidator.InvalidTemplateBodyException;
import de.dlr.shepard.v2.aas.io.AasIdtaImportResultIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * AAS admin endpoints — currently exposes the AAS1d IDTA template import.
 *
 * <p>All endpoints are {@code @RolesAllowed("instance-admin")}.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/admin/aas")
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "AAS Admin")
public class AasAdminRest {

  @Inject
  AasIdtaTemplateImportService importService;

  @Inject
  AuthenticationContext authenticationContext;

  @POST
  @Path("/import-idta-templates")
  @Operation(
      summary = "Import bundled IDTA Submodel Templates (AAS1d).",
      description =
          "Idempotently upserts the three bundled IDTA Submodel Templates "
              + "(Digital Nameplate v3.0, Technical Data v2.0, Time Series Data v1.1) "
              + "as ShepardTemplate entities with templateKind = AAS_SUBMODEL_TEMPLATE. "
              + "An entry is skipped when the live record already has identical body, "
              + "description, and tags. Returns the list of created/updated templates "
              + "and the count of skipped entries.")
  @APIResponse(
      responseCode = "200",
      description = "Import completed. Lists created/updated templates and skipped count.",
      content = @Content(schema = @Schema(implementation = AasIdtaImportResultIO.class)))
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @APIResponse(responseCode = "500", description = "Bundled template YAML could not be loaded or parsed.")
  public Response importIdtaTemplates() {
    String caller = authenticationContext.getCurrentUserName();
    if (caller == null) {
      caller = "system";
    }
    try {
      AasIdtaImportResultIO result = importService.importBundledTemplates(caller);
      return Response.ok(result).build();
    } catch (InvalidTemplateBodyException e) {
      return Response.serverError()
          .entity(Map.of("error", "Bundled template body invalid: " + String.join("; ", e.getErrors())))
          .build();
    } catch (IllegalStateException e) {
      return Response.serverError()
          .entity(Map.of("error", e.getMessage()))
          .build();
    }
  }
}
