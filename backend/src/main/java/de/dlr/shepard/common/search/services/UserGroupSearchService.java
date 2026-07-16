package de.dlr.shepard.common.search.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.io.UserGroupIO;
import de.dlr.shepard.common.search.daos.SearchDAO;
import de.dlr.shepard.common.search.io.UserGroupSearchBody;
import de.dlr.shepard.common.search.io.UserGroupSearchResult;
import de.dlr.shepard.common.search.query.Neo4jQuery;
import de.dlr.shepard.common.search.query.Neo4jQueryBuilder;
import de.dlr.shepard.common.search.query.QueryValidator;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.PaginationHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;

@RequestScoped
public class UserGroupSearchService {

  @Inject
  SearchDAO searchDAO;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public UserGroupSearchResult search(UserGroupSearchBody userGroupSearchBody) {
    Neo4jQuery selectionQuery = Neo4jQueryBuilder.userGroupSelectionQuery(userGroupSearchBody.getSearchParams().getQuery());
    QueryValidator.checkQuery(userGroupSearchBody.getSearchParams().getQuery());
    var userGroups = searchDAO.findUserGroups(selectionQuery, Constants.USERGROUP_IN_QUERY);
    var userGroupsIO = userGroups.stream().map(UserGroupIO::new).toArray(UserGroupIO[]::new);
    return new UserGroupSearchResult(userGroupsIO, userGroupSearchBody.getSearchParams());
  }

  /**
   * SEARCH-V2-4-PRE — text search returning {@link UserGroup} entities for v2 REST consumers.
   * Searches by {@code name} (case-insensitive contains). Uses Jackson for safe JSON
   * serialization of the user-supplied value.
   */
  public List<UserGroup> searchByText(String text) {
    String jsonQuery = buildNameContainsQuery(text);
    QueryValidator.checkQuery(jsonQuery);
    Neo4jQuery selectionQuery = Neo4jQueryBuilder.userGroupSelectionQuery(jsonQuery);
    return searchDAO.findUserGroups(selectionQuery, Constants.USERGROUP_IN_QUERY);
  }

  /** APISIMP-USERGROUP-SEARCH-PAGING — total count for a text search, used by the paged path. */
  public long countByText(String text) {
    String jsonQuery = buildNameContainsQuery(text);
    QueryValidator.checkQuery(jsonQuery);
    Neo4jQuery selectionQuery = Neo4jQueryBuilder.userGroupSelectionQuery(jsonQuery);
    return searchDAO.countUserGroups(selectionQuery, Constants.USERGROUP_IN_QUERY);
  }

  /** APISIMP-USERGROUP-SEARCH-PAGING — DB-backed paged text search; honours page + pageSize at Cypher level. */
  public List<UserGroup> searchByTextPaged(String text, int page, int pageSize) {
    String jsonQuery = buildNameContainsQuery(text);
    QueryValidator.checkQuery(jsonQuery);
    Neo4jQuery selectionQuery = Neo4jQueryBuilder.userGroupSelectionQuery(jsonQuery);
    PaginationHelper pagination = new PaginationHelper(page, pageSize);
    return searchDAO.findUserGroupsPaged(selectionQuery, Constants.USERGROUP_IN_QUERY, pagination);
  }

  private static String buildNameContainsQuery(String value) {
    try {
      ObjectNode cond = MAPPER.createObjectNode();
      cond.put("property", "name");
      cond.put("value", value);
      cond.put("operator", "contains");
      return MAPPER.writeValueAsString(cond);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize group search query", e);
    }
  }
}
