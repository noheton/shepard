package de.dlr.shepard.search;

import java.util.List;
import java.util.Map;

import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.util.Constants;

public class CollectionSearcher implements ISearcher {

	private SearchDAO searchDAO = new SearchDAO();
	private static final String[] colvariables = { Constants.COLLECTION_IN_QUERY };

	@Override
	public ResponseBody search(SearchBody searchBody, String userName) {
		String selectionQuery = Neo4jEmitter.emitCollectionSelectionQuery(searchBody.getSearchParams().getQuery(),
				userName);
		List<Map<String, Long>> idDictionary = searchDAO.buildQueryAndGetIdDictionaryFromQuery(selectionQuery,
				colvariables);
		ResultTriple[] resultTriples = new ResultTriple[idDictionary.size()];
		for (int i = 0; i < idDictionary.size(); i++) {
			resultTriples[i] = new ResultTriple(idDictionary.get(i).get(Constants.COLLECTION_IN_QUERY));
		}
		ResponseBody responseBody = new ResponseBody(resultTriples, searchBody.getSearchParams());
		return responseBody;
	}

}
