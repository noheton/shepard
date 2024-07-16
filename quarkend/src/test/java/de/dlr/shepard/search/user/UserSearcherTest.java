package de.dlr.shepard.search.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.UserIO;
import de.dlr.shepard.search.Neo4jEmitter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class UserSearcherTest extends BaseTestCase {

  @Mock
  private SearchDAO searchDAO;

  @InjectMocks
  private UserSearcher userSearcher;

  @Test
  public void searchFileContainerTest() {
    String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    var params = new UserSearchParams(JSONquery);
    var searchBody = new UserSearchBody(params);
    String selectionQuery = Neo4jEmitter.emitUserSelectionQuery(JSONquery);
    var user = new User("user");
    when(searchDAO.findUsers(selectionQuery, "user")).thenReturn(List.of(user));
    var actual = userSearcher.search(searchBody);
    UserIO[] result = { new UserIO(user) };
    var expected = new UserSearchResult(result, params);
    assertEquals(expected, actual);
  }
}
