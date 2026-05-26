package de.dlr.shepard.plugins.aas.admin.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.aas.entities.AasConfig;
import de.dlr.shepard.plugins.aas.io.AasConfigIO;
import de.dlr.shepard.plugins.aas.io.AasConfigPatchIO;
import de.dlr.shepard.plugins.aas.services.AasConfigService;
import de.dlr.shepard.plugins.aas.services.AasConfigService.AasPatch;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * AAS1l — admin REST surface for the AAS plugin runtime config.
 *
 * <p>Lives under {@code /v2/admin/aas/config} — exclusively
 * {@code @RolesAllowed("instance-admin")}. All response bodies
 * are {@code application/json}. Aligns with the established
 * operator-knob pattern (UH1a, N1c2, KIP1c-d per
 * {@code CLAUDE.md} "Always: surface operator knobs in the
 * admin config").
 *
 * <p><b>Key behaviour:</b>
 * <ul>
 *   <li>{@code GET /v2/admin/aas/config} — returns current config
 *       with {@code registryApiKey} masked as a boolean
 *       {@code apiKeyPresent} flag.</li>
 *   <li>{@code PATCH /v2/admin/aas/config} — RFC 7396 merge-patch;
 *       all four fields are runtime-mutable. Setting
 *       {@code "registryApiKey": null} revokes the stored key.</li>
 * </ul>
 *
 * @see AasConfigService
 * @see de.dlr.shepard.plugins.aas.entities.AasConfig
 */
@Path("/v2/admin/aas/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "AAS Admin")
public class AasConfigAdminRest {

  @Inject
  AasConfigService service;

  @GET
  @Operation(
    summary = "Read the current :AasConfig singleton.",
    description = "Returns the runtime-mutable AAS plugin config — enabled, registryUrl, " +
    "apiKeyPresent (boolean; the raw registry API key is never returned), and baseUrl. " +
    "PROV1a's ProvenanceCaptureFilter captures this GET as a READ :Activity row."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current AAS config (singleton).",
    content = @Content(schema = @Schema(implementation = AasConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getConfig() {
    AasConfig cfg = service.current();
    return Response.ok(AasConfigIO.from(cfg)).build();
  }

  @PATCH
  @Operation(
    summary = "RFC 7396 merge-patch the :AasConfig singleton.",
    description = "Patchable fields: enabled, registryUrl, registryApiKey, baseUrl. " +
    "RFC 7396 semantics — absent = leave alone, null = clear (string fields), value = replace. " +
    "Setting registryApiKey to null revokes the stored key (open registries need no auth). " +
    "The registry API key is never returned; use GET to confirm presence via apiKeyPresent. " +
    "PROV1a's ProvenanceCaptureFilter captures this PATCH as an :Activity row " +
    "(targetKind=AasConfig) so the audit trail records who changed what when."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated config returned in the same shape as GET.",
    content = @Content(schema = @Schema(implementation = AasConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patchConfig(AasConfigPatchIO body) {
    AasConfigPatchIO patch = body == null ? new AasConfigPatchIO() : body;

    AasPatch svcPatch = new AasPatch();
    svcPatch.enabled = patch.getEnabled();
    svcPatch.registryUrl = patch.getRegistryUrl();
    svcPatch.registryUrlTouched = patch.isRegistryUrlTouched();
    svcPatch.registryApiKey = patch.getRegistryApiKey();
    svcPatch.registryApiKeyTouched = patch.isRegistryApiKeyTouched();
    svcPatch.baseUrl = patch.getBaseUrl();
    svcPatch.baseUrlTouched = patch.isBaseUrlTouched();

    AasConfig saved = service.patch(svcPatch);
    return Response.ok(AasConfigIO.from(saved)).build();
  }
}
