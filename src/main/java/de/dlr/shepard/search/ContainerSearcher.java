package de.dlr.shepard.search;

import java.util.ArrayList;
import java.util.List;

import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.io.BasicEntityIO;
import de.dlr.shepard.util.Constants;

public class ContainerSearcher {

	private SearchDAO searchDAO = new SearchDAO();

	public ContainerSearchResult search(ContainerSearchBody containerSearchBody, String userName) {
		ContainerSearchParams containerSearchParams = containerSearchBody.getSearchParams();
		ContainerQueryType containerQueryType = containerSearchParams.getQueryType();
		QueryValidator.checkQuery(containerSearchBody.getSearchParams().getQuery());
		ArrayList<BasicEntityIO> resultList = new ArrayList<BasicEntityIO>();
		if (containerQueryType == null || containerQueryType.equals(ContainerQueryType.FILE)) {
			resultList.addAll(findFileContainerList(containerSearchParams, userName));
		}
		if (containerQueryType == null || containerQueryType.equals(ContainerQueryType.TIMESERIES)) {
			resultList.addAll(findTimeseriesContainerList(containerSearchParams, userName));
		}
		if (containerQueryType == null || containerQueryType.equals(ContainerQueryType.STRUCTUREDDATA)) {
			resultList.addAll(findStructuredDataContainerList(containerSearchParams, userName));
		}
		BasicEntityIO[] resultArray = resultList.toArray(new BasicEntityIO[0]);
		ContainerSearchResult containerSearchResult = new ContainerSearchResult(resultArray,
				containerSearchBody.getSearchParams());
		return containerSearchResult;
	}

	private List<BasicEntityIO> findFileContainerList(ContainerSearchParams params, String userName) {
		String neo4jSelectionQuery = Neo4jEmitter.emitFileContainerSelectionQuery(params.getQuery(), userName);
		List<FileContainer> resultContainers = searchDAO.findFileContainers(neo4jSelectionQuery,
				Constants.FILECONTAINER_IN_QUERY);
		List<BasicEntityIO> ret = resultContainers.stream().map(BasicEntityIO::new).toList();
		return ret;
	}

	private List<BasicEntityIO> findTimeseriesContainerList(ContainerSearchParams params, String userName) {
		String neo4jSelectionQuery = Neo4jEmitter.emitTimeseriesContainerSelectionQuery(params.getQuery(), userName);
		List<TimeseriesContainer> resultContainers = searchDAO.findTimeseriesContainers(neo4jSelectionQuery,
				Constants.TIMESERIESCONTAINER_IN_QUERY);
		List<BasicEntityIO> ret = resultContainers.stream().map(BasicEntityIO::new).toList();
		return ret;
	}

	private List<BasicEntityIO> findStructuredDataContainerList(ContainerSearchParams params, String userName) {
		String neo4jSelectionQuery = Neo4jEmitter.emitStructuredDataContainerSelectionQuery(params.getQuery(),
				userName);
		List<StructuredDataContainer> resultContainers = searchDAO.findStructuredDataContainers(neo4jSelectionQuery,
				Constants.STRUCTUREDDATACONTAINER_IN_QUERY);
		List<BasicEntityIO> ret = resultContainers.stream().map(BasicEntityIO::new).toList();
		return ret;
	}

}
