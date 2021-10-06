package de.dlr.shepard.search;

import java.util.List;

public class SearchUtils {

	public static String makeMongoQueryId(String mongoId) {
		return "{$oid: '" + mongoId + "'}";
	}

	public static String makeMongoQueryArray(List<String> strings) {
		if (strings.size() == 0)
			return "[]";
		String ret = "[";
		for (int i = 0; i < (strings.size() - 1); i++) {
			ret = ret + strings.get(i) + ", ";
		}
		ret = ret + strings.get(strings.size() - 1) + "]";
		return ret;
	}

}
