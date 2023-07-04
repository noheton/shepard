package de.dlr.shepard.search;

import de.dlr.shepard.neo4Core.io.BasicEntityIO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContainerSearchResult {

	private BasicEntityIO[] result;
	private ContainerSearchParams searchParams;

}
