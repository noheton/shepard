package de.dlr.shepard.common.search;

import de.dlr.shepard.common.search.container.BasicContainerAttributes;
import de.dlr.shepard.common.search.container.ContainerSearchBody;
import de.dlr.shepard.common.search.container.ContainerSearchResult;
import de.dlr.shepard.common.search.container.ContainerSearcher;
import de.dlr.shepard.common.search.unified.ResponseBody;
import de.dlr.shepard.common.search.unified.SearchBody;
import de.dlr.shepard.common.search.unified.Searcher;
import de.dlr.shepard.common.search.user.UserSearchBody;
import de.dlr.shepard.common.search.user.UserSearchResult;
import de.dlr.shepard.common.search.user.UserSearcher;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path(Constants.SEARCH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
public class SearchRest {

  @Context
  private SecurityContext securityContext;

  private Searcher searcher;
  private UserSearcher userSearcher;
  private ContainerSearcher containerSearcher;

  SearchRest() {}

  @Inject
  public SearchRest(Searcher searcher, UserSearcher userSearcher, ContainerSearcher containerSearcher) {
    this.searcher = searcher;
    this.userSearcher = userSearcher;
    this.containerSearcher = containerSearcher;
  }

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
    Log.infof("Search for %s with query: %s", body.getSearchParams().getQueryType(), body.getSearchParams().getQuery());
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
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  public Response searchContainers(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = ContainerSearchBody.class))
    ) @Valid ContainerSearchBody containerSearchBody,
    @QueryParam(Constants.QP_PAGE) Integer page,
    @QueryParam(Constants.QP_SIZE) Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) BasicContainerAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    Log.infof(
      "Search for page %d and size %d of containers of type %s ordering by %s with query: %s",
      page,
      size,
      containerSearchBody.getSearchParams().getQueryType(),
      orderBy,
      containerSearchBody.getSearchParams().getQuery()
    );

    var paginationParams = new QueryParamHelper();
    if (page != null && size != null) paginationParams = paginationParams.withPageAndSize(page, size);
    if (orderBy != null) paginationParams = paginationParams.withOrderByAttribute(orderBy, orderDesc);
    ContainerSearchResult ret = containerSearcher.search(
      containerSearchBody,
      paginationParams,
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
    Log.infof("Search for users with query: %s", userSearchBody.getSearchParams().getQuery());
    UserSearchResult ret = userSearcher.search(userSearchBody);
    return Response.ok(ret).build();
  }
}
