package de.dlr.shepard.v2.config.resources;

import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigRegistry;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * APISIMP-JUPYTER-PUBLIC-CONFIG-GENERIC — generic public read-only config surface
 * for any authenticated user. Replaces bespoke per-feature public read endpoints
 * (e.g. {@code GET /v2/jupyter/config}) with a single registry-driven surface:
 *
 * <ul>
 *   <li>{@code GET /v2/config/{feature}} — returns the config for a feature whose
 *   {@link ConfigDescriptor#publicRead()} is {@code true}.</li>
 * </ul>
 *
 * <p>Only features with {@code publicRead() == true} are served here; all others
 * return 404. The admin write surface ({@code PATCH /v2/admin/config/{feature}})
 * remains on its own resource with the {@code instance-admin} role gate.
 */
@Path("/v2/config")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Config")
public class PublicConfigRest {

  static final String PROBLEM_TYPE_NOT_FOUND = "/problems/config.not-found";

  @Inject
  ConfigRegistry registry;

  @GET
  @Path("/{feature}")
  @Operation(
    operationId = "getPublicConfig",
    summary = "Read the public config for a feature (any authenticated user).",
    description =
      "Returns the current config shape for any feature whose descriptor carries " +
      "`publicRead = true`. 404 when the feature is unknown or not publicly readable. " +
      "Auth: any authenticated user — no admin role required.\n\n" +
      "Currently public-readable: `jupyter` (`enabled` + `hubUrl`, used by the " +
      "unified data-references table to decide whether to render the " +
      "'Open in JupyterHub' affordance on `.ipynb` rows)."
  )
  @APIResponse(responseCode = "200", description = "Current config shape for the feature.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(
    responseCode = "404",
    description = "Feature unknown or not publicly readable (RFC 7807).",
    content = @Content(mediaType = "application/problem+json")
  )
  public Response getConfig(@PathParam("feature") String feature) {
    ConfigDescriptor<?> descriptor = registry.resolve(feature).orElse(null);
    if (descriptor == null || !descriptor.publicRead()) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found",
        Response.Status.NOT_FOUND,
        "No public config is available for feature '" + feature + "'.");
    }
    return Response.ok(descriptor.currentShape()).build();
  }
}
