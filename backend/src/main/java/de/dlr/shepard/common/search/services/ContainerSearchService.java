package de.dlr.shepard.common.search.services;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import de.dlr.shepard.common.search.daos.SearchDAO;
import de.dlr.shepard.common.search.io.ContainerSearchBody;
import de.dlr.shepard.common.search.io.ContainerSearchParams;
import de.dlr.shepard.common.search.io.ContainerSearchResult;
import de.dlr.shepard.common.search.query.Neo4jQueryBuilder;
import de.dlr.shepard.common.search.query.QueryValidator;
import de.dlr.shepard.common.util.PaginationHelper;
import de.dlr.shepard.common.util.SortingHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;

@RequestScoped
public class ContainerSearchService {

  @Inject
  SearchDAO searchDAO;

  @Inject
  UserService userService;

  public ContainerSearchResult search(
    ContainerSearchBody containerSearchBody,
    PaginationHelper pagination,
    SortingHelper sortOrder
  ) {
    User user = userService.getCurrentUser();

    ContainerSearchParams containerSearchParams = containerSearchBody.getSearchParams();
    QueryValidator.checkQuery(containerSearchBody.getSearchParams().getQuery());
    String neo4jSelectionQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      containerSearchParams.getQuery(),
      containerSearchParams.getQueryType(),
      sortOrder,
      user.getUsername()
    );
    List<BasicContainerIO> resultList = findContainerList(neo4jSelectionQuery, containerSearchParams, pagination);
    Integer totalResultCount = searchDAO.getContainerTotalCount(
      neo4jSelectionQuery,
      containerSearchParams.getQueryType().getTypeAlias()
    );
    ContainerSearchResult containerSearchResult = new ContainerSearchResult(
      resultList,
      containerSearchBody.getSearchParams(),
      totalResultCount
    );
    return containerSearchResult;
  }

  private List<BasicContainerIO> findContainerList(
    String neo4jSelectionQuery,
    ContainerSearchParams searchParams,
    PaginationHelper pagination
  ) {
    List<BasicContainer> resultContainers = searchDAO.findContainers(
      neo4jSelectionQuery,
      pagination,
      searchParams.getQueryType().getTypeAlias()
    );
    List<BasicContainerIO> ret = resultContainers.stream().map(BasicContainerIO::new).toList();
    return ret;
  }
}
