package de.dlr.shepard.search.unified;

public interface ISearcher {

	ResponseBody search(SearchBody searchBody, String userName);

}
