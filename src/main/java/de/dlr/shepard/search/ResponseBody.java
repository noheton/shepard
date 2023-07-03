package de.dlr.shepard.search;

import de.dlr.shepard.neo4Core.io.BasicEntityIO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseBody {

	private ResultTriple[] resultSet;
	private BasicEntityIO[] results;
	private SearchParams searchParams;

}
