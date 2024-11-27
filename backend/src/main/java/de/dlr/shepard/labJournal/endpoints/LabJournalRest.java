package de.dlr.shepard.labJournal.endpoints;

import com.arjuna.ats.jta.exceptions.NotImplementedException;
import de.dlr.shepard.labJournal.entities.LabJournal;
import de.dlr.shepard.labJournal.io.LabJournalIO;
import de.dlr.shepard.labJournal.services.LabJournalService;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.services.DataObjectService;
import de.dlr.shepard.util.Constants;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
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

@Consumes(MediaType.APPLICATION_JSON)
@Path("/" + Constants.LAB_JOURNALS)
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class LabJournalRest {

  private LabJournalService labJournalService;
  private DataObjectService dataObjectService;

  @Context
  private SecurityContext securityContext;

  @Inject
  public LabJournalRest(LabJournalService labJournalService, DataObjectService dataObjectService) {
    this.labJournalService = labJournalService;
    this.dataObjectService = dataObjectService;
  }

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
    DataObject dataObject = dataObjectService.getDataObjectByShepardId(dataObjectId);
    if (null == dataObject) return Response.status(Status.NOT_FOUND).build();
    ArrayList<LabJournalIO> result = new ArrayList<LabJournalIO>();
    for (var labJournal : dataObject.getLabJournals()) {
      result.add(new LabJournalIO(labJournal));
    }
    return Response.ok(result).build();
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
  public Response getLabJournalById(@PathParam(Constants.LAB_JOURNAL_ID) long labJournalId) {
    LabJournal labJournal = labJournalService.getLabJournal(labJournalId);
    if (null == labJournal) return Response.status(Status.NOT_FOUND).build();
    return Response.ok(new LabJournalIO(labJournal)).build();
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
  ) {
    DataObject dataObject = dataObjectService.getDataObjectByShepardId(labJournal.getDataObjectId());
    if (null == dataObject) return Response.status(Status.NOT_FOUND).build();
    LabJournalIO labJournalIO = new LabJournalIO(
      labJournalService.CreateLabJournal(labJournal, securityContext.getUserPrincipal().getName())
    );
    return Response.ok(labJournalIO).status(Status.CREATED).build();
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
      content = @Content(
        schema = @Schema(
          name = "Lab journal content",
          properties = { @SchemaProperty(name = "journalContent", type = SchemaType.STRING) }
        )
      )
    ) @Valid LabJournalIO labJournalIO
  ) {
    LabJournal labJournal = labJournalService.getLabJournal(labJournalId);
    if (null == labJournal) return Response.status(Status.NOT_FOUND).build();
    String userName = securityContext.getUserPrincipal().getName();
    if (!labJournal.getCreatedBy().getUsername().equals(userName)) return Response.status(Status.FORBIDDEN).build();
    labJournal = labJournalService.updateLabJournal(
      labJournalId,
      labJournalIO.getJournalContent(),
      securityContext.getUserPrincipal().getName()
    );
    return Response.ok(new LabJournalIO(labJournal)).build();
  }

  @DELETE
  @Path("/{" + Constants.LAB_JOURNAL_ID + "}")
  @Tag(name = Constants.LAB_JOURNAL)
  @Operation(description = "Delete a lab journal")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.LAB_JOURNAL_ID)
  public Response deleteLabJournal(@PathParam(Constants.LAB_JOURNAL_ID) long labJournalId) {
    LabJournal labJournal = labJournalService.getLabJournal(labJournalId);
    if (null == labJournal) return Response.status(Status.NOT_FOUND).build();
    String userName = securityContext.getUserPrincipal().getName();
    if (!labJournal.getCreatedBy().getUsername().equals(userName)) return Response.status(Status.FORBIDDEN).build();
    return labJournalService.deleteLabJournal(labJournalId, securityContext.getUserPrincipal().getName())
      ? Response.status(Status.NO_CONTENT).build()
      : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }
}
