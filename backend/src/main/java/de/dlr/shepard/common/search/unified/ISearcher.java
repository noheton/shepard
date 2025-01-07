package de.dlr.shepard.common.search.unified;

public interface ISearcher {
  ResponseBody search(SearchBody searchBody, String userName);
}
