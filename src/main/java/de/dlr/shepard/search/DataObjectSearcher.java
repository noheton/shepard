package de.dlr.shepard.search;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.TraversalRules;

public class DataObjectSearcher implements ISearcher {

	private SearchDAO searchDAO = new SearchDAO();
	private static final String[] coldovariables = { Constants.COLLECTION_IN_QUERY, Constants.DATAOBJECT_IN_QUERY };
	private static final String[] dovariables = { Constants.DATAOBJECT_IN_QUERY };

	@Override
	public ResponseBody search(SearchBody searchBody, String userName) {
		Set<ResultTriple> resultTriples = new HashSet<>();
		SearchScope[] scopes = searchBody.getScopes();
		String searchBodyQuery = searchBody.getSearchParams().getQuery();
		String selectionQuery;
		for (SearchScope scope : scopes) {
			// no CollectionId and no DataObjectId given
			if (scope.getCollectionId() == null && scope.getDataObjectId() == null) {
				selectionQuery = Neo4jEmitter.emitDataObjectSelectionQuery(searchBodyQuery, userName);
				List<Map<String, Long>> idDictionaries = searchDAO.buildQueryAndGetIdDictionaryFromQuery(selectionQuery,
						coldovariables);
				for (Map<String, Long> idDictionary : idDictionaries) {
					ResultTriple resultTriple = new ResultTriple(idDictionary.get(Constants.COLLECTION_IN_QUERY),
							idDictionary.get(Constants.DATAOBJECT_IN_QUERY));
					resultTriples.add(resultTriple);
				}
			}
			// CollectionId given but no DataObjectId
			else if (scope.getCollectionId() != null && scope.getDataObjectId() == null) {
				selectionQuery = Neo4jEmitter.emitCollectionDataObjectSelectionQuery(scope.getCollectionId(),
						searchBodyQuery, userName);
				List<Map<String, Long>> idDictionaries = searchDAO.buildQueryAndGetIdDictionaryFromQuery(selectionQuery,
						dovariables);
				for (Map<String, Long> idDictionary : idDictionaries) {
					ResultTriple resultTriple = new ResultTriple(scope.getCollectionId(),
							idDictionary.get(Constants.DATAOBJECT_IN_QUERY));
					resultTriples.add(resultTriple);
				}
			}
			// CollectionId and DataObjectId given
			else if (scope.getCollectionId() != null && scope.getDataObjectId() != null) {
				// search according to TraversalRules
				if (scope.getTraversalRules().length != 0) {
					for (TraversalRules traversalRules : scope.getTraversalRules()) {
						selectionQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectSelectionQuery(scope,
								traversalRules, searchBodyQuery, userName);
						List<Map<String, Long>> idDictionaries = searchDAO
								.buildQueryAndGetIdDictionaryFromQuery(selectionQuery, dovariables);
						for (Map<String, Long> idDictionary : idDictionaries) {
							ResultTriple resultTriple = new ResultTriple(scope.getCollectionId(),
									idDictionary.get(Constants.DATAOBJECT_IN_QUERY));
							resultTriples.add(resultTriple);
						}
					}
				}
				// no TraversalRules given
				else {
					selectionQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectSelectionQuery(scope,
							searchBodyQuery, userName);
					List<Map<String, Long>> idDictionaries = searchDAO
							.buildQueryAndGetIdDictionaryFromQuery(selectionQuery, dovariables);
					for (Map<String, Long> idDictionary : idDictionaries) {
						ResultTriple resultTriple = new ResultTriple(scope.getCollectionId(),
								idDictionary.get(Constants.DATAOBJECT_IN_QUERY));
						resultTriples.add(resultTriple);
					}
				}
			}
		}
		ResultTriple[] resultTripleArray = new ResultTriple[resultTriples.size()];
		resultTriples.toArray(resultTripleArray);
		ResponseBody ret = new ResponseBody(resultTripleArray, searchBody.getSearchParams());
		return ret;
	}
}
