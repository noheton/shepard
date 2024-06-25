package de.dlr.shepard.search;

import de.dlr.shepard.exceptions.InvalidBodyException;

public class QueryValidator {

	private QueryValidator() {
	}

	private static final String[] keywords = { "match", "detach", "delete", "create", "where", "drop", "call",
			"constraint", "index", "merge", "return", "set", "show", "terminate", "union", "unwind" };

	private static final String[] delimiters = { " ", "(", "{", "\n", "[" };

	public static boolean checkQuery(String query) {
		for (String keyword : keywords)
			for (String delimiter : delimiters)
				if (query.toLowerCase().contains(keyword + delimiter))
					throw new InvalidBodyException("query must not contain " + keyword + delimiter);
		return true;
	}

}
