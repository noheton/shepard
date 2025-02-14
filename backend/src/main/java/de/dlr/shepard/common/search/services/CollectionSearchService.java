package de.dlr.shepard.common.search.services;

import de.dlr.shepard.common.search.daos.SearchDAO;
import de.dlr.shepard.common.search.io.CollectionSearchBody;
import de.dlr.shepard.common.search.io.CollectionSearchParams;
import de.dlr.shepard.common.search.io.CollectionSearchResult;
import de.dlr.shepard.common.search.query.Neo4jQueryBuilder;
import de.dlr.shepard.common.search.query.QueryValidator;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.PaginationHelper;
import de.dlr.shepard.common.util.SortingHelper;
import de.dlr.shepard.context.collection.io.CollectionIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;

@RequestScoped
public class CollectionSearchService {

  private SearchDAO searchDAO;

  CollectionSearchService() {}

  @Inject
  public CollectionSearchService(SearchDAO searchDAO) {
    this.searchDAO = searchDAO;
  }

  public CollectionSearchResult search(
    CollectionSearchBody collectionSearchBody,
    PaginationHelper pagination,
    SortingHelper sortOrder,
    String userName
  ) {
    CollectionSearchParams collectionSearchParams = collectionSearchBody.getSearchParams();
    String query = collectionSearchParams.getQuery();
    QueryValidator.checkQuery(query);
    String neo4jSelectionQuery = Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(query, userName, sortOrder);
    List<CollectionIO> resultList = searchDAO
      .findCollections(neo4jSelectionQuery, pagination, Constants.COLLECTION_IN_QUERY)
      .stream()
      .map(CollectionIO::new)
      .toList();
    Integer totalResultCount = searchDAO.getCollectionTotalCount(neo4jSelectionQuery, Constants.COLLECTION_IN_QUERY);
    CollectionIO[] resultArray = resultList.toArray(new CollectionIO[0]);
    CollectionSearchResult collectionSearchResult = new CollectionSearchResult(
      resultArray,
      collectionSearchParams,
      totalResultCount
    );
    return collectionSearchResult;
  }
}
