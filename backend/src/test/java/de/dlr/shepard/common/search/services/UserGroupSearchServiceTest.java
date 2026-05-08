package de.dlr.shepard.common.search.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.io.UserGroupIO;
import de.dlr.shepard.common.search.daos.SearchDAO;
import de.dlr.shepard.common.search.io.UserGroupSearchBody;
import de.dlr.shepard.common.search.io.UserGroupSearchParams;
import de.dlr.shepard.common.search.io.UserGroupSearchResult;
import de.dlr.shepard.common.search.query.Neo4jQuery;
import de.dlr.shepard.common.search.query.Neo4jQueryBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class UserGroupSearchServiceTest extends BaseTestCase {

  @Mock
  private SearchDAO searchDAO;

  @InjectMocks
  private UserGroupSearchService userGroupSearcher;

  @Test
  public void searchUserGroupTest() {
    String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    var params = new UserGroupSearchParams(JSONquery);
    var searchBody = new UserGroupSearchBody(params);
    Neo4jQuery selectionQuery = Neo4jQueryBuilder.userGroupSelectionQuery(JSONquery);
    var userGroup = new UserGroup(123);
    when(searchDAO.findUserGroups(selectionQuery, "userGroup")).thenReturn(List.of(userGroup));
    var actual = userGroupSearcher.search(searchBody);
    UserGroupIO[] result = { new UserGroupIO(userGroup) };
    var expected = new UserGroupSearchResult(result, params);
    assertEquals(expected, actual);
  }
}
