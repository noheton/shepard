package de.dlr.shepard.context.labJournal.endpoints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
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
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
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
@Path(Constants.SHEPARD_API + "/" + Constants.LAB_JOURNAL_ENTRIES)
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class LabJournalEntryRest {

  @Inject
  LabJournalEntryService labJournalEntryService;

  @Inject
  DataObjectService dataObjectService;

  @Context
  private SecurityContext securityContext;

  @Inject
  Validator validator;

  @Inject
  ObjectMapper objectMapper;

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

  /**
   * P21 - partial-update primitive on LabJournalEntry per strategy (c) in
   * backlog P21 (see aidocs/16-dispatcher-backlog.md and aidocs/26-crud-consistency.md
   * finding #1). Ships PATCH additively in /v1/ alongside the existing PUT, which
   * keeps its full-replace semantics unchanged for backwards compatibility. Uses
   * RFC 7396 JSON Merge Patch: missing top-level fields are preserved, present
   * fields are replaced, explicit JSON null clears the field. Permission check
   * matches PUT (creator-only); Bean Validation runs against the merged result,
   * not the partial input. Mirrors the P21 Collection pilot exactly.
   */
  @PATCH
  @Path("/{" + Constants.LAB_JOURNAL_ENTRY_ID + "}")
  @Consumes({ Constants.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON })
  @Tag(name = Constants.LAB_JOURNAL_ENTRY)
  @Operation(
    summary = "Partially update a lab journal",
    description = "Applies an RFC 7396 JSON Merge Patch to the lab journal entry. The request body is a partial " +
    "LabJournalEntry: fields present in the body replace the corresponding fields on the entity, fields absent " +
    "from the body are left unchanged, and explicit JSON null clears the field. The merged result is then " +
    "Bean-Validated; constraint violations on the final state return 400. Returns the full updated entity. " +
    "Accepts both application/merge-patch+json (preferred, per RFC 7396) and application/json in /v1/; " +
    "future /v2/ APIs will require application/merge-patch+json."
  )
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = LabJournalEntryIO.class))
  )
  @APIResponse(
    description = "bad request",
    responseCode = "400",
    content = @Content(schema = @Schema(implementation = InvalidHtmlResponse.class))
  )
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.LAB_JOURNAL_ENTRY_ID)
  public Response patchLabJournal(
    @PathParam(Constants.LAB_JOURNAL_ENTRY_ID) @NotNull @PositiveOrZero Long labJournalEntryId,
    @RequestBody(
      required = true,
      description = "Partial LabJournalEntry (RFC 7396). Every field is optional; absent fields are preserved.",
      content = @Content(
        mediaType = Constants.APPLICATION_MERGE_PATCH_JSON,
        schema = @Schema(implementation = LabJournalEntryIO.class)
      )
    ) JsonNode patch
  ) {
    if (patch == null || !patch.isObject()) {
      throw new InvalidBodyException("PATCH body must be a JSON object (RFC 7396 JSON Merge Patch)");
    }

    LabJournalEntry existing = labJournalEntryService.getLabJournalEntry(labJournalEntryId);
    String userName = securityContext.getUserPrincipal().getName();
    if (!existing.getCreatedBy().getUsername().equals(userName)) return Response.status(Status.FORBIDDEN).build();

    LabJournalEntryIO merged = new LabJournalEntryIO(existing);
    try {
      objectMapper.readerForUpdating(merged).readValue(patch);
    } catch (JsonProcessingException e) {
      throw new InvalidBodyException("Invalid JSON Merge Patch body: %s".formatted(e.getOriginalMessage()));
    } catch (IOException e) {
      throw new InvalidBodyException("Could not read JSON Merge Patch body: %s".formatted(e.getMessage()));
    }

    Set<ConstraintViolation<LabJournalEntryIO>> violations = validator.validate(merged);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }

    if (!HtmlSanitizer.isSafeHtml(merged.getJournalContent())) {
      String sanitizedHtml = HtmlSanitizer.cleanHtmlString(merged.getJournalContent());
      return Response.status(Status.BAD_REQUEST)
        .entity(new InvalidHtmlResponse(merged.getJournalContent(), sanitizedHtml))
        .build();
    }

    LabJournalEntry updated = labJournalEntryService.updateLabJournalEntry(labJournalEntryId, merged.getJournalContent());
    return Response.ok(new LabJournalEntryIO(updated)).build();
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
