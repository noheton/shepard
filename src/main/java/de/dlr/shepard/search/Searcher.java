package de.dlr.shepard.search;

public class Searcher {

	private ISearcher structuredDataSearcher = new StructuredDataSearcher();
	private ISearcher collectionSearcher = new CollectionSearcher();
	private ISearcher dataObjectSearcher = new DataObjectSearcher();
	private ISearcher referenceSearcher = new ReferenceSearcher();

	public ResponseBody search(SearchBody searchBody, String userName) {
		ResponseBody ret = null;
		QueryValidator.checkQuery(searchBody.getSearchParams().getQuery());
		QueryType queryType = searchBody.getSearchParams().getQueryType();
		switch (queryType) {
		case StructuredData:
			ret = structuredDataSearcher.search(searchBody, userName);
			break;
		case Collection:
			ret = collectionSearcher.search(searchBody, userName);
			break;
		case DataObject:
			ret = dataObjectSearcher.search(searchBody, userName);
			break;
		case Reference:
			ret = referenceSearcher.search(searchBody, userName);
			break;
		default:
			break;
		}
		return ret;
	}

}
