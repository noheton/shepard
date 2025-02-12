package de.dlr.shepard.common.search.services;

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

  private SearchDAO searchDAO;

  ContainerSearchService() {}

  @Inject
  public ContainerSearchService(SearchDAO searchDAO) {
    this.searchDAO = searchDAO;
  }

  public ContainerSearchResult search(
    ContainerSearchBody containerSearchBody,
    PaginationHelper pagination,
    SortingHelper sortOrder,
    String userName
  ) {
    ContainerSearchParams containerSearchParams = containerSearchBody.getSearchParams();
    QueryValidator.checkQuery(containerSearchBody.getSearchParams().getQuery());
    String neo4jSelectionQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      containerSearchParams.getQuery(),
      containerSearchParams.getQueryType(),
      sortOrder,
      userName
    );
    List<BasicContainerIO> resultList = findContainerList(neo4jSelectionQuery, containerSearchParams, pagination);
    Integer totalResultCount = searchDAO.getContainerTotalCount(
      neo4jSelectionQuery,
      containerSearchParams.getQueryType().getTypeAlias()
    );
    BasicContainerIO[] resultArray = resultList.toArray(new BasicContainerIO[0]);
    ContainerSearchResult containerSearchResult = new ContainerSearchResult(
      resultArray,
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
