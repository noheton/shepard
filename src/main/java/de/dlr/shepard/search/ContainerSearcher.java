package de.dlr.shepard.search;

import java.util.List;

import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.util.Constants;

public class ContainerSearcher {

	private SearchDAO searchDAO = new SearchDAO();

	public ContainerSearchResult search(ContainerSearchBody containerSearchBody, String userName) {
		ContainerSearchParams containerSearchParams = containerSearchBody.getSearchParams();
		ContainerSearchResult containerSearchResult = new ContainerSearchResult();
		ContainerQueryType containerQueryType = containerSearchParams.getQueryType();
		QueryValidator.checkQuery(containerSearchBody.getSearchParams().getQuery());
		if (containerQueryType == null || containerQueryType.equals(ContainerQueryType.FILE)) {
			containerSearchResult.setFileContainers(findFileContainers(containerSearchParams, userName));
		}
		if (containerQueryType == null || containerQueryType.equals(ContainerQueryType.TIMESERIES)) {
			containerSearchResult.setTimeseriesContainers(findTimeseriesContainers(containerSearchParams, userName));
		}
		if (containerQueryType == null || containerQueryType.equals(ContainerQueryType.STRUCTUREDDATA)) {
			containerSearchResult
					.setStructuredDataContainers(findStructuredDataContainers(containerSearchParams, userName));
		}
		return containerSearchResult;
	}

	private FileContainerIO[] findFileContainers(ContainerSearchParams params, String userName) {
		String neo4jSelectionQuery = Neo4jEmitter.emitFileContainerSelectionQuery(params.getQuery(), userName);
		List<FileContainer> resultContainers = searchDAO.findFileContainers(neo4jSelectionQuery,
				Constants.FILECONTAINER_IN_QUERY);
		FileContainerIO[] fileContainers = new FileContainerIO[resultContainers.size()];
		for (int i = 0; i < resultContainers.size(); i++)
			fileContainers[i] = new FileContainerIO(resultContainers.get(i));
		return fileContainers;
	}

	private TimeseriesContainerIO[] findTimeseriesContainers(ContainerSearchParams params, String userName) {
		String neo4jSelectionQuery = Neo4jEmitter.emitTimeseriesContainerSelectionQuery(params.getQuery(), userName);
		List<TimeseriesContainer> resultContainers = searchDAO.findTimeseriesContainers(neo4jSelectionQuery,
				Constants.TIMESERIESCONTAINER_IN_QUERY);
		TimeseriesContainerIO[] timeseriesContainers = new TimeseriesContainerIO[resultContainers.size()];
		for (int i = 0; i < resultContainers.size(); i++)
			timeseriesContainers[i] = new TimeseriesContainerIO(resultContainers.get(i));
		return timeseriesContainers;
	}

	private StructuredDataContainerIO[] findStructuredDataContainers(ContainerSearchParams params, String userName) {
		String neo4jSelectionQuery = Neo4jEmitter.emitStructuredDataContainerSelectionQuery(params.getQuery(),
				userName);
		List<StructuredDataContainer> resultContainers = searchDAO.findStructuredDataContainers(neo4jSelectionQuery,
				Constants.STRUCTUREDDATACONTAINER_IN_QUERY);
		StructuredDataContainerIO[] structuredDataContainers = new StructuredDataContainerIO[resultContainers.size()];
		for (int i = 0; i < resultContainers.size(); i++)
			structuredDataContainers[i] = new StructuredDataContainerIO(resultContainers.get(i));
		return structuredDataContainers;
	}

}
