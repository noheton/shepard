package de.dlr.shepard.plugins.ai.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.ai.entities.AiCapabilityConfig;
import de.dlr.shepard.plugins.ai.io.AiCapabilityConfigIO;
import de.dlr.shepard.plugins.ai.services.AiCapabilityConfigService;
import de.dlr.shepard.spi.ai.AiCapability;
import io.quarkus.logging.Log;
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
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * AI1 — admin REST surface for the LLM capability config.
 *
 * <p>All endpoints under {@code /v2/admin/ai/...} require the
 * {@code instance-admin} role. The API key field is masked in GET
 * responses (value {@code "***"}) — the raw key is never returned.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /v2/admin/ai/capabilities} — all capability configs</li>
 *   <li>{@code GET  /v2/admin/ai/capabilities/{capability}} — one slot</li>
 *   <li>{@code PATCH /v2/admin/ai/capabilities/{capability}} — merge-patch</li>
 * </ul>
 */
@Path("/v2/admin/ai")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class AiAdminRest {

  @Inject
  AiCapabilityConfigService service;

  @GET
  @Path("/capabilities")
  @Operation(
    summary = "List all AI capability slot configurations.",
    description = "Returns the runtime config for every known AiCapability slot. " +
    "The raw API key is never returned — the response carries apiKeySet=true/false " +
    "and the masked sentinel '***' in the apiKey field when a key is configured."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of capability configs (one per AiCapability value).",
    content = @Content(schema = @Schema(implementation = AiCapabilityConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getAllCapabilities() {
    List<AiCapabilityConfig> configs = service.getAllConfigs();
    List<AiCapabilityConfigIO> ios = configs.stream()
      .map(AiCapabilityConfigIO::from)
      .toList();
    return Response.ok(ios).build();
  }

  @GET
  @Path("/capabilities/{capability}")
  @Operation(
    summary = "Get the config for one AI capability slot.",
    description = "Returns the runtime config for the named capability. If no config " +
    "has been set for this slot yet, a disabled skeleton is seeded and returned. " +
    "Unknown capability names return 404."
  )
  @APIResponse(
    responseCode = "200",
    description = "Capability config.",
    content = @Content(schema = @Schema(implementation = AiCapabilityConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @APIResponse(responseCode = "404", description = "Unknown capability name.")
  public Response getCapability(@PathParam("capability") String capability) {
    AiCapability cap = parseCapability(capability);
    if (cap == null) {
      return notFound(capability);
    }
    AiCapabilityConfig cfg = service.getConfig(cap);
    return Response.ok(AiCapabilityConfigIO.from(cfg)).build();
  }

  @PATCH
  @Path("/capabilities/{capability}")
  @Operation(
    summary = "RFC 7396 merge-patch one AI capability slot.",
    description = "Patchable fields: endpointUrl, model, apiKey, transport, " +
    "guardrailsPrefix, guardrailsSuffix, maxTokens, temperature, enabled. " +
    "RFC 7396 semantics — absent = leave alone. Sending apiKey='***' " +
    "(the masked sentinel from GET) leaves the stored key unchanged. " +
    "Mutations are captured as :Activity rows by PROV1a's ProvenanceCaptureFilter."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated capability config.",
    content = @Content(schema = @Schema(implementation = AiCapabilityConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @APIResponse(responseCode = "404", description = "Unknown capability name.")
  public Response patchCapability(
    @PathParam("capability") String capability,
    AiCapabilityConfigIO body
  ) {
    AiCapability cap = parseCapability(capability);
    if (cap == null) {
      return notFound(capability);
    }

    AiCapabilityConfigIO patch = body != null ? body : new AiCapabilityConfigIO();
    AiCapabilityConfig saved = service.upsertConfig(cap, patch);

    Log.infof(
      "AI1: PATCH /v2/admin/ai/capabilities/%s applied (enabled=%s)",
      capability,
      saved.getEnabled()
    );
    return Response.ok(AiCapabilityConfigIO.from(saved)).build();
  }

  // ─── helpers ─────────────────────────────────────────────────────

  private static AiCapability parseCapability(String name) {
    if (name == null) return null;
    try {
      return AiCapability.valueOf(name.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static Response notFound(String capability) {
    ProblemJson body = new ProblemJson(
      "/problems/ai.capability.unknown",
      "Unknown AI capability",
      Status.NOT_FOUND.getStatusCode(),
      "'" + capability + "' is not a known AiCapability. Valid values: TEXT, FAST_TEXT, " +
      "IMAGE_GEN, VISION, EMBEDDING, STRUCTURED.",
      null
    );
    return Response.status(Status.NOT_FOUND).type("application/problem+json").entity(body).build();
  }
}
