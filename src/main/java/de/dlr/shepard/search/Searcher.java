package de.dlr.shepard.search;

public class Searcher {

	private ISearcher structuredDataSearcher = new StructuredDataSearcher();

	public ResponseBody search(SearchBody searchBody) {
		ResponseBody ret = null;
		QueryType queryType = searchBody.getSearchParams().getQueryType();
		switch (queryType) {
		case StructuredData:
			ret = structuredDataSearcher.search(searchBody);
			break;
		default:
			break;
		}
		return ret;
	}

}
