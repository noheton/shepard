package de.dlr.shepard.common.search.services;

import de.dlr.shepard.auth.users.io.UserGroupIO;
import de.dlr.shepard.common.search.daos.SearchDAO;
import de.dlr.shepard.common.search.io.UserGroupSearchBody;
import de.dlr.shepard.common.search.io.UserGroupSearchResult;
import de.dlr.shepard.common.search.query.Neo4jQueryBuilder;
import de.dlr.shepard.common.search.query.QueryValidator;
import de.dlr.shepard.common.util.Constants;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@RequestScoped
public class UserGroupSearchService {

  @Inject
  SearchDAO searchDAO;

  public UserGroupSearchResult search(UserGroupSearchBody userGroupSearchBody) {
    String selectionQuery = Neo4jQueryBuilder.userGroupSelectionQuery(userGroupSearchBody.getSearchParams().getQuery());
    QueryValidator.checkQuery(userGroupSearchBody.getSearchParams().getQuery());
    var userGroups = searchDAO.findUserGroups(selectionQuery, Constants.USERGROUP_IN_QUERY);
    var userGroupsIO = userGroups.stream().map(UserGroupIO::new).toArray(UserGroupIO[]::new);
    return new UserGroupSearchResult(userGroupsIO, userGroupSearchBody.getSearchParams());
  }
}
