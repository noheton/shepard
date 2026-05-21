package de.dlr.shepard.plugins.wikiwriter.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.plugins.wikiwriter.io.WikiWriteRequestIO;
import de.dlr.shepard.plugins.wikiwriter.io.WikiWriteResponseIO;
import de.dlr.shepard.plugins.wikiwriter.services.WikiWriterService;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * WW1 — REST resource for the wiki-writer plugin.
 *
 * <p>Exposes a single endpoint:
 * {@code POST /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/wiki-write}
 *
 * <p>The endpoint summarises the target DataObject and its Collection siblings
 * using the configured LLM TEXT capability and writes the result as a
 * {@code LabJournalEntry} on the DataObject.
 *
 * <p><b>Auth.</b> {@code @Authenticated} at class level (401 when no JWT/API-key).
 * Write access on the DataObject's parent Collection is enforced per-request
 * via {@link PermissionsService#isAccessAllowedForDataObjectAppId} — consistent
 * with the established {@code /v2/} pattern in {@code DataObjectV2Rest}.
 *
 * <p><b>LLM availability.</b> Returns 503 when no {@code LlmProvider} is
 * present or when the TEXT capability is not configured. The plugin still
 * starts normally when {@code shepard-plugin-ai} is absent.
 */
@Path("/v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/wiki-write")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Wiki Writer (v2)")
public class WikiWriterRest {

  @Inject
  WikiWriterService wikiWriterService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @POST
  @Operation(
    summary = "Generate and write an AI lab journal entry for a DataObject.",
    description =
      "Uses the configured LLM TEXT capability to summarise the target DataObject " +
      "and its sibling DataObjects in the same Collection, then writes the result " +
      "as a LabJournalEntry on the target DataObject.\n\n" +
      "The generated Markdown entry captures: DataObject name, status, attributes, " +
      "description, and relationships (predecessors, successors, siblings).\n\n" +
      "Optional body fields:\n" +
      "  - `extraInstruction` — appended to the user-instruction layer; e.g. " +
      "    \"Focus on anomalies.\"\n" +
      "  - `maxTokens` — clamped to [128, 4096]; defaults to 1024.\n\n" +
      "Auth: Write permission on the parent Collection (DataObjects inherit " +
      "Collection permissions).\n\n" +
      "Returns 503 when the LLM provider or TEXT capability is not configured.\n\n" +
      "Side effects: creates a LabJournalEntry node linked to the DataObject. " +
      "The LLM provider writes an :AiActivity provenance node; its appId is " +
      "returned in `activityAppId`."
  )
  @APIResponse(
    responseCode = "200",
    description = "Journal entry created. Response includes the entry id, generated text, and provenance.",
    content = @Content(schema = @Schema(implementation = WikiWriteResponseIO.class))
  )
  @APIResponse(responseCode = "400", description = "Bad request — invalid maxTokens range.")
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent Collection.")
  @APIResponse(responseCode = "404", description = "No Collection or DataObject with that appId.")
  @APIResponse(responseCode = "503", description = "LLM provider not configured or TEXT capability unavailable.")
  public Response wikiWrite(
    @PathParam("collectionAppId") String collectionAppId,
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @RequestBody(
      required = false,
      content = @Content(schema = @Schema(implementation = WikiWriteRequestIO.class))
    ) WikiWriteRequestIO body,
    @Context SecurityContext sc
  ) {
    // Resolve appIds to OGM ids.
    Long collectionOgmId = resolveOrNull(collectionAppId);
    Long dataObjectOgmId = resolveOrNull(dataObjectAppId);
    if (collectionOgmId == null || dataObjectOgmId == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    // Gate: Write permission on the DataObject (inherited from parent Collection).
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObjectAppId, AccessType.Write, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    // Guard: LLM provider must be available.
    if (!wikiWriterService.isAvailable()) {
      Log.warnf("WW1: wiki-write requested but LLM TEXT capability is not available");
      return Response
        .status(Response.Status.SERVICE_UNAVAILABLE)
        .entity("{\"error\": \"LLM TEXT capability is not configured. Deploy shepard-plugin-ai and configure the TEXT capability.\"}")
        .build();
    }

    WikiWriteRequestIO request = body != null ? body : new WikiWriteRequestIO();

    try {
      WikiWriteResponseIO result = wikiWriterService.wikiWrite(collectionOgmId, dataObjectOgmId, request);
      return Response.ok(result).build();
    } catch (de.dlr.shepard.spi.ai.LlmException e) {
      Log.errorf(e, "WW1: LLM call failed for DataObject %s", dataObjectAppId);
      return Response
        .status(Response.Status.SERVICE_UNAVAILABLE)
        .entity("{\"error\": \"LLM call failed: " + e.getMessage() + "\"}")
        .build();
    }
  }

  private Long resolveOrNull(String appId) {
    try {
      return entityIdResolver.resolveLong(appId);
    } catch (NotFoundException nfe) {
      return null;
    }
  }
}
