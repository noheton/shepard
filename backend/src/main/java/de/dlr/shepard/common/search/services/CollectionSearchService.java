package de.dlr.shepard.common.search.services;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.search.daos.SearchDAO;
import de.dlr.shepard.common.search.endpoints.BasicCollectionAttributes;
import de.dlr.shepard.common.search.query.Neo4jQuery;
import de.dlr.shepard.common.search.query.Neo4jQueryBuilder;
import de.dlr.shepard.common.search.query.QueryValidator;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.PaginationHelper;
import de.dlr.shepard.common.util.SortingHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

@RequestScoped
public class CollectionSearchService {

  @Inject
  SearchDAO searchDAO;

  @Inject
  UserService userService;

  public PaginatedCollectionList search(
    String collectionSearchQuery,
    Optional<Integer> page,
    Optional<Integer> pageSize,
    BasicCollectionAttributes orderBy,
    Boolean orderDesc
  ) {
    User user = userService.getCurrentUser();

    QueryValidator.checkQuery(collectionSearchQuery);
    PaginationHelper pagination = null;
    if (page.isPresent() && pageSize.isPresent()) pagination = new PaginationHelper(page.get(), pageSize.get());
    Neo4jQuery neo4jSelectionQuery = Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(
      collectionSearchQuery,
      user.getUsername(),
      new SortingHelper(orderBy, orderDesc)
    );
    List<Collection> resultList = searchDAO.findCollections(
      neo4jSelectionQuery,
      pagination,
      Constants.COLLECTION_IN_QUERY
    );
    Integer totalResultCount = searchDAO.getCollectionTotalCount(neo4jSelectionQuery, Constants.COLLECTION_IN_QUERY);
    return new PaginatedCollectionList(
      resultList,
      totalResultCount,
      collectionSearchQuery,
      page,
      pageSize,
      orderBy,
      orderDesc
    );
  }
}
