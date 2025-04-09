package de.dlr.shepard.common.search.services;

import de.dlr.shepard.auth.users.io.UserIO;
import de.dlr.shepard.common.search.daos.SearchDAO;
import de.dlr.shepard.common.search.io.UserSearchBody;
import de.dlr.shepard.common.search.io.UserSearchResult;
import de.dlr.shepard.common.search.query.Neo4jQueryBuilder;
import de.dlr.shepard.common.search.query.QueryValidator;
import de.dlr.shepard.common.util.Constants;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@RequestScoped
public class UserSearchService {

  @Inject
  SearchDAO searchDAO;

  public UserSearchResult search(UserSearchBody userSearchBody) {
    String selectionQuery = Neo4jQueryBuilder.userSelectionQuery(userSearchBody.getSearchParams().getQuery());
    QueryValidator.checkQuery(userSearchBody.getSearchParams().getQuery());
    var users = searchDAO.findUsers(selectionQuery, Constants.USER_IN_QUERY);
    var usersIO = users.stream().map(UserIO::new).toArray(UserIO[]::new);
    return new UserSearchResult(usersIO, userSearchBody.getSearchParams());
  }
}
