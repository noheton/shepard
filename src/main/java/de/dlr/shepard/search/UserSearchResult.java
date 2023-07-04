package de.dlr.shepard.search;

import de.dlr.shepard.neo4Core.io.UserIO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSearchResult {

	private UserIO[] results;
	private UserSearchParams searchParams;

}
