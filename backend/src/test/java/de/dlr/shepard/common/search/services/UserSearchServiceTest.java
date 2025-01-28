package de.dlr.shepard.common.search.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.io.UserIO;
import de.dlr.shepard.common.search.daos.SearchDAO;
import de.dlr.shepard.common.search.io.UserSearchBody;
import de.dlr.shepard.common.search.io.UserSearchParams;
import de.dlr.shepard.common.search.io.UserSearchResult;
import de.dlr.shepard.common.search.query.Neo4jQueryBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class UserSearchServiceTest extends BaseTestCase {

  @Mock
  private SearchDAO searchDAO;

  @InjectMocks
  private UserSearchService userSearcher;

  @Test
  public void searchFileContainerTest() {
    String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    var params = new UserSearchParams(JSONquery);
    var searchBody = new UserSearchBody(params);
    String selectionQuery = Neo4jQueryBuilder.userSelectionQuery(JSONquery);
    var user = new User("user");
    when(searchDAO.findUsers(selectionQuery, "user")).thenReturn(List.of(user));
    var actual = userSearcher.search(searchBody);
    UserIO[] result = { new UserIO(user) };
    var expected = new UserSearchResult(result, params);
    assertEquals(expected, actual);
  }
}
