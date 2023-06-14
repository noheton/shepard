package de.dlr.shepard.search;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.util.Constants;

public class ReferenceSearcher implements ISearcher {

	private SearchDAO searchDAO = new SearchDAO();
	private static final String[] coldobrvariables = { Constants.COLLECTION_IN_QUERY, Constants.DATAOBJECT_IN_QUERY,
			Constants.REFERENCE_IN_QUERY };
	private static final String[] dobrvariables = { Constants.DATAOBJECT_IN_QUERY, Constants.REFERENCE_IN_QUERY };

	@Override
	public ResponseBody search(SearchBody searchBody, String userName) {
		Set<ResultTriple> resultTriples = new HashSet<>();
		SearchScope[] scopes = searchBody.getScopes();
		String searchBodyQuery = searchBody.getSearchParams().getQuery();
		String selectionQuery;
		for (SearchScope scope : scopes) {
			// no CollectionId and no DataObjectId given
			if (scope.getCollectionId() == null && scope.getDataObjectId() == null) {
				selectionQuery = Neo4jEmitter.emitBasicReferenceSelectionQuery(searchBodyQuery, userName);
				List<Map<String, Long>> idDictionaries = searchDAO.buildQueryAndGetIdDictionaryFromQuery(selectionQuery,
						coldobrvariables);
				for (Map<String, Long> idDictionary : idDictionaries) {
					ResultTriple resultTriple = new ResultTriple(idDictionary.get(Constants.COLLECTION_IN_QUERY),
							idDictionary.get(Constants.DATAOBJECT_IN_QUERY),
							idDictionary.get(Constants.REFERENCE_IN_QUERY));
					resultTriples.add(resultTriple);
				}
			}
			// CollectionId given but no DataObjectId
			else if (scope.getCollectionId() != null && scope.getDataObjectId() == null) {
				selectionQuery = Neo4jEmitter.emitCollectionBasicReferenceSelectionQuery(searchBodyQuery,
						scope.getCollectionId(), userName);
				List<Map<String, Long>> idDictionaries = searchDAO.buildQueryAndGetIdDictionaryFromQuery(selectionQuery,
						dobrvariables);
				for (Map<String, Long> idDictionary : idDictionaries) {
					ResultTriple resultTriple = new ResultTriple(scope.getCollectionId(),
							idDictionary.get(Constants.DATAOBJECT_IN_QUERY),
							idDictionary.get(Constants.REFERENCE_IN_QUERY));
					resultTriples.add(resultTriple);
				}
			}
			// CollectionId and DataObjectId given
			else if (scope.getCollectionId() != null && scope.getDataObjectId() != null) {
				// search according to TraversalRules
				if (scope.getTraversalRules().length != 0) {
					for (int j = 0; j < scope.getTraversalRules().length; j++) {
						selectionQuery = Neo4jEmitter.emitCollectionDataObjectBasicReferenceSelectionQuery(scope,
								scope.getTraversalRules()[j], searchBodyQuery, userName);
						List<Map<String, Long>> idDictionaries = searchDAO
								.buildQueryAndGetIdDictionaryFromQuery(selectionQuery, dobrvariables);
						for (Map<String, Long> idDictionary : idDictionaries) {
							ResultTriple resultTriple = new ResultTriple(scope.getCollectionId(),
									idDictionary.get(Constants.DATAOBJECT_IN_QUERY),
									idDictionary.get(Constants.REFERENCE_IN_QUERY));
							resultTriples.add(resultTriple);
						}
					}
				}
				// no TraversalRules given
				else {
					selectionQuery = Neo4jEmitter.emitCollectionDataObjectReferenceSelectionQuery(scope,
							searchBodyQuery, userName);
					List<Map<String, Long>> idDictionaries = searchDAO
							.buildQueryAndGetIdDictionaryFromQuery(selectionQuery, dobrvariables);
					for (Map<String, Long> idDictionary : idDictionaries) {
						ResultTriple resultTriple = new ResultTriple(scope.getCollectionId(),
								idDictionary.get(Constants.DATAOBJECT_IN_QUERY),
								idDictionary.get(Constants.REFERENCE_IN_QUERY));
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
