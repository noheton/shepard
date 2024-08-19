package de.dlr.shepard.search.user;

import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.neo4Core.io.UserIO;
import de.dlr.shepard.search.Neo4jEmitter;
import de.dlr.shepard.search.QueryValidator;
import de.dlr.shepard.util.Constants;
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
