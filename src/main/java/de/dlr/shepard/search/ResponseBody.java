package de.dlr.shepard.search;

import lombok.Data;

@Data
public class ResponseBody {

	private ResultTriple[] resultSet;
	private SearchParams searchParams;

}
