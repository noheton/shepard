package de.dlr.shepard.common.search.container;

import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import de.dlr.shepard.common.search.Neo4jEmitter;
import de.dlr.shepard.common.search.QueryValidator;
import de.dlr.shepard.common.search.SearchDAO;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@RequestScoped
public class ContainerSearcher {

  private SearchDAO searchDAO;

  ContainerSearcher() {}

  @Inject
  public ContainerSearcher(SearchDAO searchDAO) {
    this.searchDAO = searchDAO;
  }

  public ContainerSearchResult search(ContainerSearchBody containerSearchBody, String userName) {
    ContainerSearchParams containerSearchParams = containerSearchBody.getSearchParams();
    ContainerQueryType containerQueryType = containerSearchParams.getQueryType();
    QueryValidator.checkQuery(containerSearchBody.getSearchParams().getQuery());
    List<BasicContainerIO> resultList =
      switch (containerQueryType) {
        case FILE -> findFileContainerList(containerSearchParams, userName);
        case TIMESERIES -> findTimeseriesContainerList(containerSearchParams, userName);
        case STRUCTUREDDATA -> findStructuredDataContainerList(containerSearchParams, userName);
        default -> new ArrayList<>();
      };
    BasicContainerIO[] resultArray = resultList.toArray(new BasicContainerIO[0]);
    ContainerSearchResult containerSearchResult = new ContainerSearchResult(
      resultArray,
      containerSearchBody.getSearchParams()
    );
    return containerSearchResult;
  }

  private List<BasicContainerIO> findFileContainerList(ContainerSearchParams params, String userName) {
    String neo4jSelectionQuery = Neo4jEmitter.emitFileContainerSelectionQuery(params.getQuery(), userName);
    List<FileContainer> resultContainers = searchDAO.findFileContainers(
      neo4jSelectionQuery,
      Constants.FILECONTAINER_IN_QUERY
    );
    List<BasicContainerIO> ret = resultContainers.stream().map(BasicContainerIO::new).toList();
    return ret;
  }

  private List<BasicContainerIO> findTimeseriesContainerList(ContainerSearchParams params, String userName) {
    String neo4jSelectionQuery = Neo4jEmitter.emitTimeseriesContainerSelectionQuery(params.getQuery(), userName);
    List<TimeseriesContainer> resultContainers = searchDAO.findTimeseriesContainers(
      neo4jSelectionQuery,
      Constants.TIMESERIESCONTAINER_IN_QUERY
    );
    List<BasicContainerIO> ret = resultContainers.stream().map(BasicContainerIO::new).toList();
    return ret;
  }

  private List<BasicContainerIO> findStructuredDataContainerList(ContainerSearchParams params, String userName) {
    String neo4jSelectionQuery = Neo4jEmitter.emitStructuredDataContainerSelectionQuery(params.getQuery(), userName);
    List<StructuredDataContainer> resultContainers = searchDAO.findStructuredDataContainers(
      neo4jSelectionQuery,
      Constants.STRUCTUREDDATACONTAINER_IN_QUERY
    );
    List<BasicContainerIO> ret = resultContainers.stream().map(BasicContainerIO::new).toList();
    return ret;
  }
}
