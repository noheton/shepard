package de.dlr.shepard.v2.svdx.resources;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.v2.svdx.io.SvdxIngestRequestIO;
import de.dlr.shepard.v2.svdx.io.SvdxIngestResponseIO;
import de.dlr.shepard.v2.svdx.services.SvdxCsvIngestionService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
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
 * REST surface for {@code MFFD-PLUGIN-SVDX-CSV-INGEST-1}.
 *
 * <p>{@code POST /v2/svdx/ingest} — given two singleton FileReferences
 * already attached to the same DataObject ({@code .svdx} + its
 * TwinCAT-Scope-Export-Tool {@code .csv} sibling), parse the CSV,
 * mint a {@code TimeseriesContainer} when none is supplied, and write
 * each CSV column as a channel into TimescaleDB via the existing
 * {@code TimeseriesService.saveDataPoints} path.
 *
 * <p>Idempotent: re-calling with the same {@code svdxFileAppId} +
 * {@code csvFileAppId} on the same DataObject is a no-op that returns
 * the existing {@code TimeseriesReference}.
 *
 * <p>This is the operator-facing companion of AAB1's manifest-only
 * tier-1 parser ({@code MFFD-PLUGIN-SVDX-1}): the SVDX file alone
 * carries channel headers but the binary samples are proprietary; the
 * CSV the Scope Export Tool generates is the supported sample path.
 */
@Path("/v2/svdx")
@RequestScoped
@Tag(name = "SVDX (Beckhoff TwinCAT Scope) ingest (v2)")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SvdxIngestRest {

  @Inject
  SvdxCsvIngestionService svc;

  @POST
  @Path("/ingest")
  @Operation(
      summary = "Ingest a TwinCAT Scope CSV (sibling of a .svdx) into TimescaleDB.",
      description =
          "Given two singleton FileReferences already attached to the same DataObject — " +
          "the `.svdx` project file (manifest already parsed by `shepard-plugin-fileformat-svdx`) " +
          "and the operator-generated `.csv` sibling from the Beckhoff TwinCAT Scope Export Tool — " +
          "parse the CSV, build per-channel 5-tuple identities from the SVDX/CSV metadata " +
          "(measurement=projectName, device=NetID, location=Port, symbolicName=SymbolName, " +
          "field=channel display name), and write the rows into TimescaleDB via the existing " +
          "`TimeseriesService` path.\n\n" +
          "Auth: Write permission on the DataObject named by `dataObjectAppId`. " +
          "Both FileReferences must already be attached to that DataObject.\n\n" +
          "Idempotency: the new TimeseriesReference name is deterministically built from the " +
          "two source appIds (`svdx-ingest:<svdxAppId>+<csvAppId>`). Re-calling with the same " +
          "pair short-circuits and returns the existing reference with `idempotentReplay=true`.\n\n" +
          "Side effects: a new TimeseriesContainer is minted in the same Collection when " +
          "`tsContainerAppId` is omitted. A new TimeseriesReference is attached to the DataObject. " +
          "Per-channel rows land in TimescaleDB. `ProvenanceCaptureFilter` records a CREATE Activity " +
          "for the call. No new annotation predicates beyond what AAB1 already emits.")
  @RequestBody(
      required = true,
      description = "Request body — the two singleton appIds + parent DataObject appId.",
      content = @Content(schema = @Schema(implementation = SvdxIngestRequestIO.class)))
  @APIResponse(
      responseCode = "201",
      description = "TimeseriesReference created (or replayed when idempotent).",
      content = @Content(schema = @Schema(implementation = SvdxIngestResponseIO.class)))
  @APIResponse(responseCode = "400", description = "Body missing/invalid, files don't belong to the DataObject, or CSV unparseable.")
  @APIResponse(responseCode = "401", description = "Unauthenticated.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "DataObject or one of the FileReferences was not found.")
  public Response ingest(SvdxIngestRequestIO req, @Context SecurityContext securityContext) {
    String caller = securityContext != null && securityContext.getUserPrincipal() != null
        ? securityContext.getUserPrincipal().getName()
        : null;
    if (caller == null || caller.isBlank()) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      SvdxIngestResponseIO out = svc.ingest(req, caller);
      return Response.status(Response.Status.CREATED).entity(out).build();
    } catch (InvalidBodyException badBody) {
      return Response.status(Response.Status.BAD_REQUEST).entity(badBody.getMessage()).build();
    } catch (InvalidPathException missing) {
      return Response.status(Response.Status.NOT_FOUND).entity(missing.getMessage()).build();
    } catch (SecurityException forbidden) {
      return Response.status(Response.Status.FORBIDDEN).entity(forbidden.getMessage()).build();
    }
  }
}
