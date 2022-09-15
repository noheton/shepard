package de.dlr.shepard.search;

import java.util.HashSet;
import java.util.List;
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
		String searchQuery;
		for (SearchScope scope : scopes) {
			// no CollectionId and no DataObjectId given
			if (scope.getCollectionId() == null && scope.getDataObjectId() == null) {
				searchQuery = Neo4jEmitter.emitDataObjectQuery(searchBodyQuery, userName);
				List<Long[]> idTuples = searchDAO.getIdsFromQuery(searchQuery, coldovariables);
				for (Long[] tuple : idTuples) {
					ResultTriple resultTriple = new ResultTriple();
					resultTriple.setCollectionId(tuple[0]);
					resultTriple.setDataObjectId(tuple[1]);
					resultTriples.add(resultTriple);
				}
			}
			// CollectionId given but no DataObjectId
			else if (scope.getCollectionId() != null && scope.getDataObjectId() == null) {
				searchQuery = Neo4jEmitter.emitCollectionDataObjectQuery(scope.getCollectionId(), searchBodyQuery,
						userName);
				List<Long[]> dataObjectIds = searchDAO.getIdsFromQuery(searchQuery, dovariables);
				for (Long[] dataObjectId : dataObjectIds) {
					ResultTriple resultTriple = new ResultTriple();
					resultTriple.setCollectionId(scope.getCollectionId());
					resultTriple.setDataObjectId(dataObjectId[0]);
					resultTriples.add(resultTriple);
				}
			}
			// CollectionId and DataObjectId given
			else if (scope.getCollectionId() != null && scope.getDataObjectId() != null) {
				// search according to TraversalRules
				if (scope.getTraversalRules().length != 0) {
					for (TraversalRules traversalRules : scope.getTraversalRules()) {
						searchQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectQuery(scope, traversalRules,
								searchBodyQuery, userName);
						List<Long[]> dataObjectIds = searchDAO.getIdsFromQuery(searchQuery, dovariables);
						for (Long[] dataObjectId : dataObjectIds) {
							ResultTriple resultTriple = new ResultTriple();
							resultTriple.setCollectionId(scope.getCollectionId());
							resultTriple.setDataObjectId(dataObjectId[0]);
							resultTriples.add(resultTriple);
						}
					}
				}
				// no TraversalRules given
				else {
					searchQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectQuery(scope, searchBodyQuery,
							userName);
					List<Long[]> dataObjectIds = searchDAO.getIdsFromQuery(searchQuery, dovariables);
					for (Long[] dataObjectId : dataObjectIds) {
						ResultTriple resultTriple = new ResultTriple();
						resultTriple.setCollectionId(scope.getCollectionId());
						resultTriple.setDataObjectId(dataObjectId[0]);
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
