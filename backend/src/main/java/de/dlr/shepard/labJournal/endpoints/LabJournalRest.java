package de.dlr.shepard.labJournal.endpoints;

import com.arjuna.ats.jta.exceptions.NotImplementedException;
import de.dlr.shepard.labJournal.io.LabJournalIO;
import de.dlr.shepard.util.Constants;
import jakarta.enterprise.context.RequestScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Consumes(MediaType.APPLICATION_JSON)
@Path("/" + Constants.LAB_JOURNALS)
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class LabJournalRest {

  @GET
  @Path("/")
  @Tag(name = Constants.LAB_JOURNAL)
  @Operation(description = "Get all lab journals in a data object")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = LabJournalIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response getLabJournalsByCollection(@QueryParam(Constants.DATA_OBJECT_ID) long dataObjectId)
    throws NotImplementedException {
    throw new NotImplementedException();
  }

  @GET
  @Path("/{" + Constants.LAB_JOURNAL_ID + "}")
  @Tag(name = Constants.LAB_JOURNAL)
  @Operation(description = "Get a lab journal")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = LabJournalIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.LAB_JOURNAL_ID)
  public Response getLabJournalById(@PathParam(Constants.LAB_JOURNAL_ID) long labJournalId)
    throws NotImplementedException {
    throw new NotImplementedException();
  }

  @POST
  @Path("/")
  @Tag(name = Constants.LAB_JOURNAL)
  @Operation(description = "Create a lab journal in a data object")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = LabJournalIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response createLabJournal(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = LabJournalIO.class))
    ) @Valid LabJournalIO labJournal
  ) throws NotImplementedException {
    throw new NotImplementedException();
  }

  @PUT
  @Path("/{" + Constants.LAB_JOURNAL_ID + "}")
  @Tag(name = Constants.LAB_JOURNAL)
  @Operation(description = "Update a lab journal")
  @APIResponse(
    description = "updated",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = LabJournalIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.LAB_JOURNAL_ID)
  public Response updateLabJournal(
    @PathParam(Constants.LAB_JOURNAL_ID) long labJournalId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = LabJournalIO.class))
    ) @Valid LabJournalIO LabJournal
  ) throws NotImplementedException {
    throw new NotImplementedException();
  }

  @DELETE
  @Path("/{" + Constants.LAB_JOURNAL_ID + "}")
  @Tag(name = Constants.LAB_JOURNAL)
  @Operation(description = "Delete a lab journal")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.LAB_JOURNAL_ID)
  public Response deleteLabJournal(@PathParam(Constants.LAB_JOURNAL_ID) long labJournalId)
    throws NotImplementedException {
    throw new NotImplementedException();
  }
}
