package de.dlr.shepard.search;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.dlr.shepard.exceptions.ShepardParserException;
import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.util.Constants;

public class ReferenceSearcher implements ISearcher {

	private SearchDAO searchDAO = new SearchDAO();
	private static final String[] coldobrvariables = { Constants.COLLECTION_IN_QUERY, Constants.DATAOBJECT_IN_QUERY,
			Constants.REFERENCE_IN_QUERY };
	private static final String[] dobrvariables = { Constants.DATAOBJECT_IN_QUERY, Constants.REFERENCE_IN_QUERY };

	@Override
	public ResponseBody search(SearchBody searchBody, String userName) throws ShepardParserException {
		Set<ResultTriple> resultTriples = new HashSet<>();
		SearchScope[] scopes = searchBody.getScopes();
		String searchBodyQuery = searchBody.getSearchParams().getQuery();
		String searchQuery;
		for (SearchScope scope : scopes) {
			// no CollectionId and no DataObjectId given
			if (scope.getCollectionId() == null && scope.getDataObjectId() == null) {
				searchQuery = Neo4jEmitter.emitBasicReferenceQuery(searchBodyQuery, userName);
				List<Long[]> idTuples = searchDAO.getIdsFromQuery(searchQuery, coldobrvariables);
				for (Long[] tuple : idTuples) {
					ResultTriple resultTriple = new ResultTriple();
					resultTriple.setCollectionId(tuple[0]);
					resultTriple.setDataObjectId(tuple[1]);
					resultTriple.setReferenceId(tuple[2]);
					resultTriples.add(resultTriple);
				}
			}
			// CollectionId given but no DataObjectId
			else if (scope.getCollectionId() != null && scope.getDataObjectId() == null) {
				searchQuery = Neo4jEmitter.emitCollectionBasicReferenceQuery(searchBodyQuery, scope.getCollectionId(),
						userName);
				List<Long[]> idTuples = searchDAO.getIdsFromQuery(searchQuery, dobrvariables);
				for (Long[] tuple : idTuples) {
					ResultTriple resultTriple = new ResultTriple();
					resultTriple.setCollectionId(scope.getCollectionId());
					resultTriple.setDataObjectId(tuple[0]);
					resultTriple.setReferenceId(tuple[1]);
					resultTriples.add(resultTriple);
				}
			}
			// CollectionId and DataObjectId given
			else if (scope.getCollectionId() != null && scope.getDataObjectId() != null) {
				// search according to TraversalRules
				if (scope.getTraversalRules().length != 0) {
					for (int j = 0; j < scope.getTraversalRules().length; j++) {
						searchQuery = Neo4jEmitter.emitCollectionDataObjectBasicReferenceQuery(scope,
								scope.getTraversalRules()[j], searchBodyQuery, userName);
						List<Long[]> idTuples = searchDAO.getIdsFromQuery(searchQuery, dobrvariables);
						for (Long[] tuple : idTuples) {
							ResultTriple resultTriple = new ResultTriple();
							resultTriple.setCollectionId(scope.getCollectionId());
							resultTriple.setDataObjectId(tuple[0]);
							resultTriple.setReferenceId(tuple[1]);
							resultTriples.add(resultTriple);
						}
					}
				}
				// no TraversalRules given
				else {
					searchQuery = Neo4jEmitter.emitCollectionDataObjectReferenceQuery(scope, searchBodyQuery, userName);
					List<Long[]> idTuples = searchDAO.getIdsFromQuery(searchQuery, dobrvariables);
					for (Long[] tuple : idTuples) {
						ResultTriple resultTriple = new ResultTriple();
						resultTriple.setCollectionId(scope.getCollectionId());
						resultTriple.setDataObjectId(tuple[0]);
						resultTriple.setReferenceId(tuple[1]);
						resultTriples.add(resultTriple);
					}
				}
			}
		}
		ResponseBody ret = new ResponseBody();
		ResultTriple[] resultTripleArray = new ResultTriple[resultTriples.size()];
		resultTriples.toArray(resultTripleArray);
		ret.setResultSet(resultTripleArray);
		ret.setSearchParams(searchBody.getSearchParams());
		return ret;
	}
}
