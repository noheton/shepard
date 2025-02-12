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
import de.dlr.shepard.context.collection.entities.Collection;
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
    QueryValidator.checkQuery(collectionSearchBody.getSearchParams().getQuery());
    String neo4jSelectionQuery = Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(
      collectionSearchParams.getQuery(),
      userName,
      sortOrder
    );
    List<CollectionIO> resultList = findCollectionList(neo4jSelectionQuery, pagination);
    Integer totalResultCount = searchDAO.getCollectionTotalCount(neo4jSelectionQuery, Constants.COLLECTION_IN_QUERY);
    CollectionIO[] resultArray = resultList.toArray(new CollectionIO[0]);
    CollectionSearchResult collectionSearchResult = new CollectionSearchResult(
      resultArray,
      collectionSearchBody.getSearchParams(),
      totalResultCount
    );
    return collectionSearchResult;
  }

  private List<CollectionIO> findCollectionList(String neo4jSelectionQuery, PaginationHelper pagination) {
    List<Collection> resultCollections = searchDAO.findCollections(
      neo4jSelectionQuery,
      pagination,
      Constants.COLLECTION_IN_QUERY
    );
    List<CollectionIO> ret = resultCollections.stream().map(CollectionIO::new).toList();
    return ret;
  }
}
