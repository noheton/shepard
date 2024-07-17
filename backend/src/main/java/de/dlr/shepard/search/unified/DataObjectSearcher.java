package de.dlr.shepard.search.unified;

import java.util.HashSet;
import java.util.Set;

import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.io.BasicEntityIO;
import de.dlr.shepard.search.Neo4jEmitter;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.TraversalRules;

public class DataObjectSearcher implements ISearcher {

	private SearchDAO searchDAO = new SearchDAO();

	@Override
	public ResponseBody search(SearchBody searchBody, String userName) {
		Set<DataObject> resultsSet = new HashSet<>();
		SearchScope[] scopes = searchBody.getScopes();
		String searchBodyQuery = searchBody.getSearchParams().getQuery();
		for (SearchScope scope : scopes) {
			// no CollectionId and no DataObjectId given
			if (scope.getCollectionId() == null && scope.getDataObjectId() == null) {
				String selectionQuery = Neo4jEmitter.emitDataObjectSelectionQuery(searchBodyQuery, userName);
				var res = searchDAO.findDataObjects(selectionQuery, Constants.DATAOBJECT_IN_QUERY);
				resultsSet.addAll(res);
			}
			// CollectionId given but no DataObjectId
			else if (scope.getCollectionId() != null && scope.getDataObjectId() == null) {
				String selectionQuery = Neo4jEmitter.emitCollectionDataObjectSelectionQuery(scope.getCollectionId(),
						searchBodyQuery, userName);
				var res = searchDAO.findDataObjects(selectionQuery, Constants.DATAOBJECT_IN_QUERY);
				resultsSet.addAll(res);
			}
			// CollectionId and DataObjectId given
			else if (scope.getCollectionId() != null && scope.getDataObjectId() != null) {
				// search according to TraversalRules
				if (scope.getTraversalRules().length != 0) {
					for (TraversalRules traversalRules : scope.getTraversalRules()) {
						String selectionQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectSelectionQuery(scope,
								traversalRules, searchBodyQuery, userName);
						var res = searchDAO.findDataObjects(selectionQuery, Constants.DATAOBJECT_IN_QUERY);
						resultsSet.addAll(res);
					}
				}
				// no TraversalRules given
				else {
					String selectionQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectSelectionQuery(scope,
							searchBodyQuery, userName);
					var res = searchDAO.findDataObjects(selectionQuery, Constants.DATAOBJECT_IN_QUERY);
					resultsSet.addAll(res);
				}
			}
		}
		DataObject[] dataObjects = resultsSet.toArray(new DataObject[0]);
		ResultTriple[] resultTriples = new ResultTriple[resultsSet.size()];
		BasicEntityIO[] results = new BasicEntityIO[resultsSet.size()];
		for (var i = 0; i < resultsSet.size(); i++) {
			resultTriples[i] = new ResultTriple(dataObjects[i].getCollection().getShepardId(),
					dataObjects[i].getShepardId());
			results[i] = new BasicEntityIO(dataObjects[i]);
		}
		ResponseBody ret = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
		return ret;
	}
}
