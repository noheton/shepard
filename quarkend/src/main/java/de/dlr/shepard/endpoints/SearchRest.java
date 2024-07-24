package de.dlr.shepard.endpoints;

import de.dlr.shepard.search.container.ContainerSearchBody;
import de.dlr.shepard.search.container.ContainerSearchResult;
import de.dlr.shepard.search.container.ContainerSearcher;
import de.dlr.shepard.search.unified.ResponseBody;
import de.dlr.shepard.search.unified.SearchBody;
import de.dlr.shepard.search.unified.Searcher;
import de.dlr.shepard.search.user.UserSearchBody;
import de.dlr.shepard.search.user.UserSearchResult;
import de.dlr.shepard.search.user.UserSearcher;
import de.dlr.shepard.util.Constants;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Slf4j
@Path(Constants.SEARCH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SearchRest {

  @Context
  private SecurityContext securityContext;

  private Searcher searcher = new Searcher();
  private UserSearcher userSearcher = new UserSearcher();
  private ContainerSearcher containerSearcher = new ContainerSearcher();

  @POST
  @Tag(name = Constants.SEARCH)
  @Operation(description = "search")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = ResponseBody.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response search(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SearchBody.class))
    ) @Valid SearchBody body
  ) {
    log.info("Search for {} with query: {}", body.getSearchParams().getQueryType(), body.getSearchParams().getQuery());
    ResponseBody ret = searcher.search(body, securityContext.getUserPrincipal().getName());
    return Response.ok(ret).build();
  }

  @POST
  @Path("/" + Constants.CONTAINERS)
  @Tag(name = Constants.SEARCH)
  @Operation(description = "Search containers")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = ContainerSearchResult.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response searchContainers(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = ContainerSearchBody.class))
    ) @Valid ContainerSearchBody containerSearchBody
  ) {
    log.info(
      "Search for containers of type {} with query: {}",
      containerSearchBody.getSearchParams().getQueryType(),
      containerSearchBody.getSearchParams().getQuery()
    );
    ContainerSearchResult ret = containerSearcher.search(
      containerSearchBody,
      securityContext.getUserPrincipal().getName()
    );
    return Response.ok(ret).build();
  }

  @POST
  @Path("/" + Constants.USERS)
  @Tag(name = Constants.SEARCH)
  @Operation(description = "Search users")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = UserSearchResult.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response searchUsers(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = UserSearchBody.class))
    ) @Valid UserSearchBody userSearchBody
  ) {
    log.info("Search for users with query: {}", userSearchBody.getSearchParams().getQuery());
    UserSearchResult ret = userSearcher.search(userSearchBody);
    return Response.ok(ret).build();
  }
}
