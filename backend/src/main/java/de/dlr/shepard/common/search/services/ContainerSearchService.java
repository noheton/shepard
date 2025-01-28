package de.dlr.shepard.common.search.services;

import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import de.dlr.shepard.common.search.daos.SearchDAO;
import de.dlr.shepard.common.search.io.ContainerSearchBody;
import de.dlr.shepard.common.search.io.ContainerSearchParams;
import de.dlr.shepard.common.search.io.ContainerSearchResult;
import de.dlr.shepard.common.search.query.Neo4jQueryBuilder;
import de.dlr.shepard.common.search.query.QueryValidator;
import de.dlr.shepard.common.util.QueryParamHelper;
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
    QueryParamHelper params,
    String userName
  ) {
    ContainerSearchParams containerSearchParams = containerSearchBody.getSearchParams();
    QueryValidator.checkQuery(containerSearchBody.getSearchParams().getQuery());
    String neo4jSelectionQuery = Neo4jQueryBuilder.containerSelectionQuery(
      containerSearchParams.getQuery(),
      containerSearchParams.getQueryType(),
      params,
      userName
    );
    List<BasicContainerIO> resultList = findContainerList(neo4jSelectionQuery, containerSearchParams, params);
    Integer totalResultCount = searchDAO.getContainerTotalCount(
      neo4jSelectionQuery,
      params,
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
    QueryParamHelper params
  ) {
    List<BasicContainer> resultContainers = searchDAO.findContainers(
      neo4jSelectionQuery,
      params,
      searchParams.getQueryType().getTypeAlias()
    );
    List<BasicContainerIO> ret = resultContainers.stream().map(BasicContainerIO::new).toList();
    return ret;
  }
}
