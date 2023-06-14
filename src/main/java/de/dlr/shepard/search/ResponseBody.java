package de.dlr.shepard.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseBody {

	private ResultTriple[] resultSet;
	private SearchParams searchParams;

}
