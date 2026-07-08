package de.dlr.shepard.v2.admin.config.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.dlr.shepard.common.exceptions.ProblemJson;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.config.spi.ConfigRegistry;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
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
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * V2CONV-A4 — generic admin-config resource. Collapses the per-feature bespoke
 * {@code *ConfigRest} classes (semantic, jupyter, sql-timeseries, ror) into a
 * single registry-driven surface:
 *
 * <ul>
 *   <li>{@code GET  /v2/admin/config} — list the registered feature keys.</li>
 *   <li>{@code GET  /v2/admin/config/{feature}} — read the current config shape.</li>
 *   <li>{@code PATCH /v2/admin/config/{feature}} — RFC-7396 merge-patch.</li>
 * </ul>
 *
 * <p>The resource resolves a {@link ConfigDescriptor} from {@link ConfigRegistry}
 * by {@code {feature}} and delegates. It centralises the cross-cutting concerns
 * the bespoke classes duplicated: the {@code @RolesAllowed("instance-admin")}
 * role gate, the {@code application/problem+json} error envelope (404 for an
 * unknown feature, 4xx for a validation failure), and — unchanged — the PROV1a
 * {@code ProvenanceCaptureFilter} {@code :Activity} capture on the 2xx PATCH
 * (admin mutations are captured by default; the resource does not call
 * {@code ProvenanceService.record()} itself, so the filter does the capture).
 *
 * <p>Adding a new runtime-configurable feature is now just adding a new
 * {@code ConfigDescriptor} bean — no new REST class.
 *
 * <p>No upstream {@code /shepard/api/} surface is touched; this is a new path on
 * the {@code /v2/} development surface. Per the v2-surface-convergence pre-prod
 * policy (aidocs/platform/191 §8), the old bespoke
 * {@code /v2/admin/<feature>/config} paths are deleted outright (no shims).
 */
@Path("/v2/admin/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class AdminConfigRest {

  static final String PROBLEM_TYPE_UNKNOWN_FEATURE = "/problems/admin.config.unknown-feature";

  @Inject
  ConfigRegistry registry;

  @GET
  @Operation(
    operationId = "listFeatures",
    summary = "List runtime-configurable features.",
    description = "Returns one row per registered config feature — the {feature} path segment " +
    "plus a description. Use the returned keys with GET|PATCH /v2/admin/config/{feature}."
  )
  @APIResponse(
    responseCode = "200",
    description = "Registered config features.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = ConfigFeatureIO.class)),
    headers = @Header(
      name = "X-Total-Count",
      description = "Total number of registered config features.",
      schema = @Schema(type = SchemaType.INTEGER)
    )
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response listFeatures() {
    List<ConfigFeatureIO> rows = registry.all()
      .stream()
      .map(d -> new ConfigFeatureIO(d.featureName(), d.description()))
      .toList();
    return Response.ok(rows).header("X-Total-Count", (long) rows.size()).build();
  }

  @GET
  @Path("/{feature}")
  @Operation(
    operationId = "getFeatureConfig",
    summary = "Read the current config for a feature.",
    description = "Resolves the ConfigDescriptor for {feature} and returns its current, " +
    "fully-resolved config shape. 404 (problem+json) when no feature is registered under that key."
  )
  @APIResponse(responseCode = "200", description = "Current config shape for the feature.")
  @APIResponse(
    responseCode = "404",
    description = "No config feature registered under {feature} (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getConfig(@PathParam("feature") String feature) {
    ConfigDescriptor<?> descriptor = registry.resolve(feature).orElse(null);
    if (descriptor == null) {
      return unknownFeature(feature);
    }
    return Response.ok(descriptor.currentShape()).build();
  }

  @PATCH
  @Path("/{feature}")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    operationId = "patchFeatureConfig",
    summary = "RFC 7396 merge-patch the config for a feature.",
    description = "Resolves the ConfigDescriptor for {feature} and applies an RFC-7396 " +
    "merge-patch: absent = leave alone, null = clear, value = replace. Returns the updated " +
    "shape (same as GET). PROV1a's ProvenanceCaptureFilter captures this PATCH as an " +
    ":Activity row. 404 (problem+json) for an unknown feature; 4xx (problem+json) for a " +
    "validation failure declared by the descriptor."
  )
  @APIResponse(responseCode = "200", description = "Updated config shape (same as GET).")
  @APIResponse(
    responseCode = "400",
    description = "A patched value failed the descriptor's validation (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(
    responseCode = "404",
    description = "No config feature registered under {feature} (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patchConfig(@PathParam("feature") String feature, JsonNode body) {
    ConfigDescriptor<?> descriptor = registry.resolve(feature).orElse(null);
    if (descriptor == null) {
      return unknownFeature(feature);
    }
    // An absent/empty body is a valid no-op merge-patch.
    JsonNode patch = (body == null || body.isNull()) ? JsonNodeFactory.instance.objectNode() : body;
    if (!patch.isObject()) {
      return problem(
        "/problems/admin.config.malformed-patch",
        "Malformed merge-patch",
        Status.BAD_REQUEST,
        "The merge-patch body must be a JSON object; got: " + patch.getNodeType()
      );
    }
    try {
      return Response.ok(descriptor.applyMergePatch(patch)).build();
    } catch (ConfigPatchException e) {
      return problem(e.getProblemType(), e.getTitle(), e.getStatus(), e.getDetail());
    }
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private Response unknownFeature(String feature) {
    return problem(
      PROBLEM_TYPE_UNKNOWN_FEATURE,
      "Unknown config feature",
      Status.NOT_FOUND,
      "No runtime-configurable feature is registered under '" + feature + "'. " +
      "List the available features with GET /v2/admin/config."
    );
  }

}
