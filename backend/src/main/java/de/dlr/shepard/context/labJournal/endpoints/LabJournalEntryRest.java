package de.dlr.shepard.context.labJournal.endpoints;

import de.dlr.shepard.common.exceptions.InvalidHtmlResponse;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HtmlSanitizer;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.io.LabJournalEntryIO;
import de.dlr.shepard.context.labJournal.services.LabJournalEntryService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

//TODO: Much of the functionality of the endpoint functions can be refactored into the LabJournal Service layer

@Consumes(MediaType.APPLICATION_JSON)
@Path("/" + Constants.LAB_JOURNAL_ENTRIES)
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class LabJournalEntryRest {

  @Inject
  LabJournalEntryService labJournalEntryService;

  @Inject
  DataObjectService dataObjectService;

  @Context
  private SecurityContext securityContext;

  @GET
  @Path("/")
  @Tag(name = Constants.LAB_JOURNAL_ENTRY)
  @Operation(description = "Get all lab journals in a data object sorted by their creation date (newest first)")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = LabJournalEntryIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.DATA_OBJECT_ID, required = true)
  public Response getLabJournalsByCollection(
    @QueryParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId
  ) {
    DataObject dataObject = dataObjectService.getDataObject(dataObjectId);
    ArrayList<LabJournalEntryIO> result = new ArrayList<LabJournalEntryIO>();
    for (var labJournalEntry : labJournalEntryService.getLabJournalEntries(dataObject)) {
      result.add(new LabJournalEntryIO(labJournalEntry));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.LAB_JOURNAL_ENTRY_ID + "}")
  @Tag(name = Constants.LAB_JOURNAL_ENTRY)
  @Operation(description = "Get a lab journal")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = LabJournalEntryIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.LAB_JOURNAL_ENTRY_ID, required = true)
  public Response getLabJournalById(
    @PathParam(Constants.LAB_JOURNAL_ENTRY_ID) @NotNull @PositiveOrZero Long labJournalEntryId
  ) {
    LabJournalEntry labJournalEntry = labJournalEntryService.getLabJournalEntry(labJournalEntryId);
    return Response.ok(new LabJournalEntryIO(labJournalEntry)).build();
  }

  @POST
  @Path("/")
  @Tag(name = Constants.LAB_JOURNAL_ENTRY)
  @Operation(description = "Create a lab journal in a data object")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = LabJournalEntryIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @APIResponse(
    description = "bad request",
    responseCode = "400",
    content = @Content(schema = @Schema(implementation = InvalidHtmlResponse.class))
  )
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.DATA_OBJECT_ID, required = true)
  public Response createLabJournal(
    @QueryParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = LabJournalEntryIO.class))
    ) @Valid LabJournalEntryIO labJournalEntryIO
  ) {
    if (!HtmlSanitizer.isSafeHtml(labJournalEntryIO.getJournalContent())) {
      String sanitizedHtml = HtmlSanitizer.cleanHtmlString(labJournalEntryIO.getJournalContent());
      return Response.status(Status.BAD_REQUEST)
        .entity(new InvalidHtmlResponse(labJournalEntryIO.getJournalContent(), sanitizedHtml))
        .build();
    }

    labJournalEntryIO = new LabJournalEntryIO(
      labJournalEntryService.createLabJournalEntry(dataObjectId, labJournalEntryIO.getJournalContent())
    );
    return Response.ok(labJournalEntryIO).status(Status.CREATED).build();
  }

  @PUT
  @Path("/{" + Constants.LAB_JOURNAL_ENTRY_ID + "}")
  @Tag(name = Constants.LAB_JOURNAL_ENTRY)
  @Operation(description = "Update a lab journal")
  @APIResponse(
    description = "updated",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = LabJournalEntryIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @APIResponse(
    description = "bad request",
    responseCode = "400",
    content = @Content(schema = @Schema(implementation = InvalidHtmlResponse.class))
  )
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.LAB_JOURNAL_ENTRY_ID)
  public Response updateLabJournal(
    @PathParam(Constants.LAB_JOURNAL_ENTRY_ID) @NotNull @PositiveOrZero Long labJournalEntryId,
    @RequestBody(
      required = true,
      content = @Content(
        schema = @Schema(
          name = "Lab journal content",
          properties = { @SchemaProperty(name = "journalContent", type = SchemaType.STRING) }
        )
      )
    ) @Valid LabJournalEntryIO labJournalEntryIO
  ) {
    LabJournalEntry labJournalEntry = labJournalEntryService.getLabJournalEntry(labJournalEntryId);
    String userName = securityContext.getUserPrincipal().getName();
    if (!labJournalEntry.getCreatedBy().getUsername().equals(userName)) return Response.status(
      Status.FORBIDDEN
    ).build();

    if (!HtmlSanitizer.isSafeHtml(labJournalEntryIO.getJournalContent())) {
      String sanitizedHtml = HtmlSanitizer.cleanHtmlString(labJournalEntryIO.getJournalContent());
      return Response.status(Status.BAD_REQUEST)
        .entity(new InvalidHtmlResponse(labJournalEntryIO.getJournalContent(), sanitizedHtml))
        .build();
    }

    labJournalEntry = labJournalEntryService.updateLabJournalEntry(
      labJournalEntryId,
      labJournalEntryIO.getJournalContent()
    );
    return Response.ok(new LabJournalEntryIO(labJournalEntry)).build();
  }

  @DELETE
  @Path("/{" + Constants.LAB_JOURNAL_ENTRY_ID + "}")
  @Tag(name = Constants.LAB_JOURNAL_ENTRY)
  @Operation(description = "Delete a lab journal")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.LAB_JOURNAL_ENTRY_ID)
  public Response deleteLabJournal(
    @PathParam(Constants.LAB_JOURNAL_ENTRY_ID) @NotNull @PositiveOrZero Long labJournalEntryId
  ) {
    labJournalEntryService.deleteLabJournalEntry(labJournalEntryId);
    return Response.status(Status.NO_CONTENT).build();
  }
}
