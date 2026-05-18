package de.dlr.shepard.v2.instance;

import de.dlr.shepard.v2.admin.ror.entities.InstanceRorConfig;
import de.dlr.shepard.v2.admin.ror.io.InstanceRorConfigIO;
import de.dlr.shepard.v2.admin.ror.services.InstanceRorConfigService;
import io.quarkus.security.Authenticated;
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
 * INST1 — public read of the instance's organisational identity.
 *
 * <p>Exposes the same wire shape as {@link de.dlr.shepard.v2.admin.ror.resources.InstanceRorConfigRest}
 * (rorId, organizationName, rorUrl) but without the {@code instance-admin}
 * role gate — every authenticated user can read who runs this instance.
 *
 * <p>The About page uses this endpoint to surface "This shepard is run by
 * ..." plus a link to ror.org for richer organisation details (the page
 * itself does the ror.org fetch client-side; the backend doesn't proxy it).
 *
 * <p>The PATCH/write surface stays on the admin endpoint — operators set
 * the ROR ID once via {@code PATCH /v2/admin/instance/ror} (or the matching
 * {@code shepard-admin instance ror set …} CLI), then everyone can read it.
 */
@Path("/v2/instance/identity")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Instance identity (INST1)")
public class InstanceIdentityRest {

  @Inject
  InstanceRorConfigService service;

  @GET
  @Operation(
    summary = "Read this instance's organisational identity (ROR-based).",
    description = "Returns the configured ROR id, organisation name, and computed " +
    "ror.org URL. All three fields are absent when no ROR id has been configured. " +
    "Public read — any authenticated user; the admin write surface lives at " +
    "PATCH /v2/admin/instance/ror."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current instance identity (may be empty if unconfigured).",
    content = @Content(schema = @Schema(implementation = InstanceRorConfigIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response getIdentity() {
    InstanceRorConfig cfg = service.current();
    return Response.ok(InstanceRorConfigIO.from(cfg)).build();
  }
}
