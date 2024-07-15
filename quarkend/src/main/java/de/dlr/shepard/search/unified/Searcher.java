package de.dlr.shepard.search.unified;

import de.dlr.shepard.search.QueryValidator;

public class Searcher {

	private ISearcher structuredDataSearcher = new StructuredDataSearcher();
	private ISearcher collectionSearcher = new CollectionSearcher();
	private ISearcher dataObjectSearcher = new DataObjectSearcher();
	private ISearcher referenceSearcher = new ReferenceSearcher();

	public ResponseBody search(SearchBody searchBody, String userName) {
		QueryValidator.checkQuery(searchBody.getSearchParams().getQuery());
		ResponseBody ret = switch (searchBody.getSearchParams().getQueryType()) {
		case StructuredData -> structuredDataSearcher.search(searchBody, userName);
		case Collection -> collectionSearcher.search(searchBody, userName);
		case DataObject -> dataObjectSearcher.search(searchBody, userName);
		case Reference -> referenceSearcher.search(searchBody, userName);
		default -> null;
		};
		return ret;
	}

}
