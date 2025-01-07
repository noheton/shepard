package de.dlr.shepard.common.search.user;

import de.dlr.shepard.auth.users.io.UserIO;
import de.dlr.shepard.common.search.Neo4jEmitter;
import de.dlr.shepard.common.search.QueryValidator;
import de.dlr.shepard.common.search.SearchDAO;
import de.dlr.shepard.common.util.Constants;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@RequestScoped
public class UserSearcher {

  private SearchDAO searchDAO;

  UserSearcher() {}

  @Inject
  public UserSearcher(SearchDAO searchDAO) {
    this.searchDAO = searchDAO;
  }

  public UserSearchResult search(UserSearchBody userSearchBody) {
    String selectionQuery = Neo4jEmitter.emitUserSelectionQuery(userSearchBody.getSearchParams().getQuery());
    QueryValidator.checkQuery(userSearchBody.getSearchParams().getQuery());
    var users = searchDAO.findUsers(selectionQuery, Constants.USER_IN_QUERY);
    var usersIO = users.stream().map(UserIO::new).toArray(UserIO[]::new);
    return new UserSearchResult(usersIO, userSearchBody.getSearchParams());
  }
}
