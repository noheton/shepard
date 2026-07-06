package de.dlr.shepard.common.search.services;

import de.dlr.shepard.auth.users.io.UserIO;
import de.dlr.shepard.common.search.daos.SearchDAO;
import de.dlr.shepard.common.search.io.UserSearchBody;
import de.dlr.shepard.common.search.io.UserSearchResult;
import de.dlr.shepard.common.search.query.Neo4jQuery;
import de.dlr.shepard.common.search.query.Neo4jQueryBuilder;
import de.dlr.shepard.common.search.query.QueryValidator;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.PaginationHelper;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;

@RequestScoped
public class UserSearchService {

  @Inject
  SearchDAO searchDAO;

  public UserSearchResult search(UserSearchBody userSearchBody) {
    Neo4jQuery selectionQuery = Neo4jQueryBuilder.userSelectionQuery(userSearchBody.getSearchParams().getQuery());
    QueryValidator.checkQuery(userSearchBody.getSearchParams().getQuery());
    var users = searchDAO.findUsers(selectionQuery, Constants.USER_IN_QUERY);
    var usersIO = users.stream().map(UserIO::new).toArray(UserIO[]::new);
    return new UserSearchResult(usersIO, userSearchBody.getSearchParams());
  }

  public PagedResponseIO<UserIO> searchPaged(UserSearchBody body, int page, int pageSize) {
    Neo4jQuery selectionQuery = Neo4jQueryBuilder.userSelectionQuery(body.getSearchParams().getQuery());
    QueryValidator.checkQuery(body.getSearchParams().getQuery());
    long total = searchDAO.getUserTotalCount(selectionQuery, Constants.USER_IN_QUERY);
    var pagination = new PaginationHelper(page, pageSize);
    List<UserIO> users = searchDAO.findUsersPaged(selectionQuery, Constants.USER_IN_QUERY, pagination)
        .stream().map(UserIO::new).toList();
    return new PagedResponseIO<>(users, total, page, pageSize);
  }
}
