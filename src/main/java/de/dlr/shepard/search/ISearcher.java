package de.dlr.shepard.search;

import de.dlr.shepard.exceptions.ShepardParserException;

public interface ISearcher {

	ResponseBody search(SearchBody searchBody, String userName) throws ShepardParserException;

}
