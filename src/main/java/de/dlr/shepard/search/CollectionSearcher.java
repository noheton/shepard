package de.dlr.shepard.search;

import java.util.List;

import de.dlr.shepard.exceptions.ShepardParserException;
import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.util.Constants;

public class CollectionSearcher implements ISearcher {

	private SearchDAO searchDAO = new SearchDAO();
	private static final String[] colvariables = { Constants.COLLECTION_IN_QUERY };

	@Override
	public ResponseBody search(SearchBody searchBody, String userName) throws ShepardParserException {
		String query = Neo4jEmitter.emitCollectionQuery(searchBody.getSearchParams().getQuery(), userName);
		List<Long[]> collectionIds = searchDAO.getIdsFromQuery(query, colvariables);
		ResultTriple[] resultTriples = new ResultTriple[collectionIds.size()];
		for (int i = 0; i < collectionIds.size(); i++) {
			resultTriples[i] = new ResultTriple();
			resultTriples[i].setCollectionId(collectionIds.get(i)[0]);
		}
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultTriples);
		responseBody.setSearchParams(searchBody.getSearchParams());
		return responseBody;
	}

}
