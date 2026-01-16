package de.dlr.shepard.common.search.endpoints;

import de.dlr.shepard.common.search.io.AnnotatableTimeseriesInContainerSearchBody;
import de.dlr.shepard.common.search.io.AnnotatableTimeseriesInContainerSearchResult;
import de.dlr.shepard.common.search.io.CollectionSearchBody;
import de.dlr.shepard.common.search.io.CollectionSearchResult;
import de.dlr.shepard.common.search.io.ContainerSearchBody;
import de.dlr.shepard.common.search.io.ContainerSearchResult;
import de.dlr.shepard.common.search.io.ResponseBody;
import de.dlr.shepard.common.search.io.SearchBody;
import de.dlr.shepard.common.search.io.UserGroupSearchBody;
import de.dlr.shepard.common.search.io.UserGroupSearchResult;
import de.dlr.shepard.common.search.io.UserSearchBody;
import de.dlr.shepard.common.search.io.UserSearchResult;
import de.dlr.shepard.common.search.services.AnnotatableTimeseriesSearchService;
import de.dlr.shepard.common.search.services.CollectionSearchService;
import de.dlr.shepard.common.search.services.ContainerSearchService;
import de.dlr.shepard.common.search.services.PaginatedCollectionList;
import de.dlr.shepard.common.search.services.SearchService;
import de.dlr.shepard.common.search.services.UserGroupSearchService;
import de.dlr.shepard.common.search.services.UserSearchService;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.PaginationHelper;
import de.dlr.shepard.common.util.SortingHelper;
import de.dlr.shepard.context.collection.io.CollectionIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
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

  @Inject
  SearchService searchService;

  @Inject
  UserSearchService userSearchService;

  @Inject
  UserGroupSearchService userGroupSearchService;

  @Inject
  ContainerSearchService containerSearchService;

  @Inject
  CollectionSearchService collectionSearchService;

  @Inject
  AnnotatableTimeseriesSearchService annotatableTimeseriesSearchService;

  @POST
  @Tag(name = Constants.SEARCH)
  @Operation(description = "search")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = ResponseBody.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  public Response search(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SearchBody.class))
    ) @Valid SearchBody body
  ) {
    ResponseBody ret = searchService.search(body);
    return Response.ok(ret).build();
  }

  @POST
  @Path("/" + Constants.COLLECTIONS)
  @Tag(name = Constants.SEARCH)
  @Operation(description = "Search collections with paginated response")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = CollectionSearchResult.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @Parameter(name = Constants.QP_PAGE, description = "Pagination starts at 0")
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE, description = "Defaults to 'createdAt'")
  @Parameter(name = Constants.QP_ORDER_DESC, description = "Defaults to 'true'")
  public Response searchCollections(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = CollectionSearchBody.class)),
      description = "You can search by ID, name or createdBy. Connect search parameters like this: {\"OR\":[{\"property\":\"name\",\"value\":\"ABC\",\"operator\":\"contains\"},{\"property\":\"id\",\"value\":123,\"operator\":\"contains\"}]}\"" +
      " "
    ) @Valid CollectionSearchBody collectionSearchBody,
    @QueryParam(Constants.QP_PAGE) @PositiveOrZero Integer page,
    @QueryParam(Constants.QP_SIZE) @Positive Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) BasicCollectionAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    PaginatedCollectionList paginatedCollectionList = collectionSearchService.search(
      collectionSearchBody.getSearchParams().getQuery(),
      Optional.ofNullable(page),
      Optional.ofNullable(size),
      Optional.ofNullable(orderBy).orElse(BasicCollectionAttributes.createdAt),
      Optional.ofNullable(orderDesc).orElse(true)
    );

    CollectionSearchResult collectionSearchResult = new CollectionSearchResult(
      paginatedCollectionList.getResults().stream().map(CollectionIO::new).toList(),
      collectionSearchBody.getSearchParams(),
      paginatedCollectionList.getTotalResults()
    );

    return Response.ok(collectionSearchResult).build();
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @Parameter(name = Constants.QP_PAGE, description = "Pagination starts at 0")
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE, description = "Defaults to 'createdAt'")
  @Parameter(name = Constants.QP_ORDER_DESC, description = "Defaults to 'true'")
  public Response searchContainers(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = ContainerSearchBody.class))
    ) @Valid ContainerSearchBody containerSearchBody,
    @QueryParam(Constants.QP_PAGE) @PositiveOrZero Integer page,
    @QueryParam(Constants.QP_SIZE) @Positive Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) BasicContainerAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    PaginationHelper pagination = null;
    if (page != null && size != null) pagination = new PaginationHelper(page, size);
    SortingHelper sortingHelper = new SortingHelper(
      Optional.ofNullable(orderBy).orElse(BasicContainerAttributes.createdAt),
      Optional.ofNullable(orderDesc).orElse(true)
    );
    ContainerSearchResult ret = containerSearchService.search(containerSearchBody, pagination, sortingHelper);
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  public Response searchUsers(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = UserSearchBody.class))
    ) @Valid UserSearchBody userSearchBody
  ) {
    UserSearchResult ret = userSearchService.search(userSearchBody);
    return Response.ok(ret).build();
  }

  @POST
  @Path("/" + Constants.USERGROUPS)
  @Tag(name = Constants.SEARCH)
  @Operation(description = "Search user groups")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = UserGroupSearchResult.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  public Response searchUserGroups(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = UserSearchBody.class))
    ) @Valid UserGroupSearchBody userGroupSearchBody
  ) {
    UserGroupSearchResult ret = userGroupSearchService.search(userGroupSearchBody);
    return Response.ok(ret).build();
  }

  @POST
  @Path("/" + Constants.CONTAINERS + "/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.ANNOTATABLE_TIMESERIES)
  @Tag(name = Constants.SEARCH)
  @Operation(description = "Search annotatable timeseries in a container")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = AnnotatableTimeseriesInContainerSearchResult.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  public Response searchAnnotatableTimeseriesInContainer(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long containerId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = AnnotatableTimeseriesInContainerSearchBody.class))
    ) @Valid AnnotatableTimeseriesInContainerSearchBody annotatableTimeseriesInContainerSearchBody
  ) {
    AnnotatableTimeseriesInContainerSearchResult ret = annotatableTimeseriesSearchService.search(
      containerId,
      annotatableTimeseriesInContainerSearchBody
    );
    return Response.ok(ret).build();
  }
}
