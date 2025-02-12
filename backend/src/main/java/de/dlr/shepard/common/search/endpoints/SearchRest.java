package de.dlr.shepard.common.search.endpoints;

import de.dlr.shepard.common.search.io.CollectionSearchBody;
import de.dlr.shepard.common.search.io.CollectionSearchResult;
import de.dlr.shepard.common.search.io.ContainerSearchBody;
import de.dlr.shepard.common.search.io.ContainerSearchResult;
import de.dlr.shepard.common.search.io.ResponseBody;
import de.dlr.shepard.common.search.io.SearchBody;
import de.dlr.shepard.common.search.io.UserSearchBody;
import de.dlr.shepard.common.search.io.UserSearchResult;
import de.dlr.shepard.common.search.services.CollectionSearchService;
import de.dlr.shepard.common.search.services.ContainerSearchService;
import de.dlr.shepard.common.search.services.SearchService;
import de.dlr.shepard.common.search.services.UserSearchService;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.PaginationHelper;
import de.dlr.shepard.common.util.SortingHelper;
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

  private SearchService searchService;
  private UserSearchService userSearchService;
  private ContainerSearchService containerSearchService;
  private CollectionSearchService collectionSearchService;

  SearchRest() {}

  @Inject
  public SearchRest(
    SearchService searchService,
    UserSearchService userSearchService,
    ContainerSearchService containerSearchService,
    CollectionSearchService collectionSearchService
  ) {
    this.searchService = searchService;
    this.userSearchService = userSearchService;
    this.containerSearchService = containerSearchService;
    this.collectionSearchService = collectionSearchService;
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
    ResponseBody ret = searchService.search(body, securityContext.getUserPrincipal().getName());
    return Response.ok(ret).build();
  }

  @POST
  @Path("/" + Constants.COLLECTIONS)
  @Tag(name = Constants.SEARCH)
  @Operation(description = "Search collections")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = CollectionSearchResult.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  public Response searchCollections(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = CollectionSearchBody.class))
    ) @Valid CollectionSearchBody collectionSearchBody,
    @QueryParam(Constants.QP_PAGE) Integer page,
    @QueryParam(Constants.QP_SIZE) Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) BasicContainerAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    Log.infof(
      "Search for page %d and size %d of collections ordering by %s with query: %s",
      page,
      size,
      orderBy,
      collectionSearchBody.getSearchParams().getQuery()
    );

    PaginationHelper pagination = null;
    if (page != null && size != null) pagination = new PaginationHelper(page, size);
    SortingHelper sortingHelper = new SortingHelper(orderBy, orderDesc);
    CollectionSearchResult ret = collectionSearchService.search(
      collectionSearchBody,
      pagination,
      sortingHelper,
      securityContext.getUserPrincipal().getName()
    );
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

    PaginationHelper pagination = null;
    if (page != null && size != null) pagination = new PaginationHelper(page, size);
    SortingHelper sortingHelper = new SortingHelper(orderBy, orderDesc);
    ContainerSearchResult ret = containerSearchService.search(
      containerSearchBody,
      pagination,
      sortingHelper,
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
    UserSearchResult ret = userSearchService.search(userSearchBody);
    return Response.ok(ret).build();
  }
}
