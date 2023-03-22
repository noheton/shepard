package de.dlr.shepard.search;

import java.util.List;

import de.dlr.shepard.neo4Core.dao.FileContainerDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataContainerDAO;
import de.dlr.shepard.neo4Core.dao.TimeseriesContainerDAO;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;

public class ContainerSearcher {

	private FileContainerDAO fileContainerDAO = new FileContainerDAO();
	private TimeseriesContainerDAO timeseriesContainerDAO = new TimeseriesContainerDAO();
	private StructuredDataContainerDAO structuredDataContainerDAO = new StructuredDataContainerDAO();

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
		String neo4jQuery = Neo4jEmitter.emitFileContainerQuery(params.getQuery(), userName);
		List<FileContainer> resultContainers = fileContainerDAO.getFileContainerByQuery(neo4jQuery);
		FileContainerIO[] fileContainers = new FileContainerIO[resultContainers.size()];
		for (int i = 0; i < resultContainers.size(); i++)
			fileContainers[i] = new FileContainerIO(resultContainers.get(i));
		return fileContainers;
	}

	private TimeseriesContainerIO[] findTimeseriesContainers(ContainerSearchParams params, String userName) {
		String neo4jQuery = Neo4jEmitter.emitTimeseriesContainerQuery(params.getQuery(), userName);
		List<TimeseriesContainer> resultContainers = timeseriesContainerDAO.getTimeseriesContainerByQuery(neo4jQuery);
		TimeseriesContainerIO[] timeseriesContainers = new TimeseriesContainerIO[resultContainers.size()];
		for (int i = 0; i < resultContainers.size(); i++)
			timeseriesContainers[i] = new TimeseriesContainerIO(resultContainers.get(i));
		return timeseriesContainers;
	}

	private StructuredDataContainerIO[] findStructuredDataContainers(ContainerSearchParams params, String userName) {
		String neo4jQuery = Neo4jEmitter.emitStructuredDataContainerQuery(params.getQuery(), userName);
		List<StructuredDataContainer> resultContainers = structuredDataContainerDAO
				.getStructuredDataContainerByQuery(neo4jQuery);
		StructuredDataContainerIO[] structuredDataContainers = new StructuredDataContainerIO[resultContainers.size()];
		for (int i = 0; i < resultContainers.size(); i++)
			structuredDataContainers[i] = new StructuredDataContainerIO(resultContainers.get(i));
		return structuredDataContainers;
	}

}
