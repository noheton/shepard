package de.dlr.shepard.endpoints;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.SemanticRepositoryIO;
import de.dlr.shepard.neo4Core.orderBy.SemanticRepositoryAttributes;
import de.dlr.shepard.neo4Core.services.SemanticRepositoryService;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.SEMANTIC_REPOSITORIES)
@RequestScoped
public class SemanticRepositoryRest {

  private SemanticRepositoryService semanticRepositoryService;

  @Context
  private SecurityContext securityContext;

  SemanticRepositoryRest() {}

  @Inject
  public SemanticRepositoryRest(SemanticRepositoryService semanticRepositoryService) {
    this.semanticRepositoryService = semanticRepositoryService;
  }

  @GET
  @Tag(name = Constants.SEMANTIC_REPOSITORY)
  @Operation(description = "Get all semantic repositories")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SemanticRepositoryIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response getAllSemanticRepositories(
    @QueryParam(Constants.QP_NAME) String name,
    @QueryParam(Constants.QP_PAGE) Integer page,
    @QueryParam(Constants.QP_SIZE) Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) SemanticRepositoryAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    var params = new QueryParamHelper();
    if (name != null) params = params.withName(name);
    if (page != null && size != null) params = params.withPageAndSize(page, size);
    if (orderBy != null) params = params.withOrderByAttribute(orderBy, orderDesc);
    var repositories = semanticRepositoryService.getAllRepositories(params);

    var result = new ArrayList<SemanticRepositoryIO>(repositories.size());
    for (var repository : repositories) {
      result.add(new SemanticRepositoryIO(repository));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.SEMANTIC_REPOSITORY_ID + "}")
  @Tag(name = Constants.SEMANTIC_REPOSITORY)
  @Operation(description = "Get semantic repository")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = SemanticRepositoryIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response getSemanticRepository(@PathParam(Constants.SEMANTIC_REPOSITORY_ID) long semanticRepositoryId) {
    var result = semanticRepositoryService.getRepository(semanticRepositoryId);
    return Response.ok(new SemanticRepositoryIO(result)).build();
  }

  @POST
  @Tag(name = Constants.SEMANTIC_REPOSITORY)
  @Operation(description = "Create a new semantic repository")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = SemanticRepositoryIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response createSemanticRepository(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SemanticRepositoryIO.class))
    ) @Valid SemanticRepositoryIO semanticRepository
  ) {
    var result = semanticRepositoryService.createRepository(
      semanticRepository,
      securityContext.getUserPrincipal().getName()
    );
    return Response.ok(new SemanticRepositoryIO(result)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.SEMANTIC_REPOSITORY_ID + "}")
  @Subscribable
  @Tag(name = Constants.SEMANTIC_REPOSITORY)
  @Operation(description = "Delete semantic repository")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  public Response deleteSemanticRepository(@PathParam(Constants.SEMANTIC_REPOSITORY_ID) long semanticRepositoryId) {
    var result = semanticRepositoryService.deleteRepository(
      semanticRepositoryId,
      securityContext.getUserPrincipal().getName()
    );
    return result ? Response.status(Status.NO_CONTENT).build() : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }
}
