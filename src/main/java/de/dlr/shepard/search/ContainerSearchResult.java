package de.dlr.shepard.search;

import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContainerSearchResult {

	private FileContainerIO[] fileContainers;
	private StructuredDataContainerIO[] structuredDataContainers;
	private TimeseriesContainerIO[] timeseriesContainers;

}
