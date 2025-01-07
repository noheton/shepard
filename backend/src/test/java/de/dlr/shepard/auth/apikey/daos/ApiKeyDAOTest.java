package de.dlr.shepard.auth.apikey.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.apikey.entities.ApiKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class ApiKeyDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private ApiKeyDAO dao = new ApiKeyDAO();

  @Test
  public void findTest() {
    var uid = UUID.randomUUID();
    var a = new ApiKey(uid);

    when(session.load(ApiKey.class, uid, 1)).thenReturn(a);
    var actual = dao.find(uid);
    assertEquals(a, actual);
  }

  @Test
  public void deleteTest_Successful() {
    var uid = UUID.randomUUID();
    var a = new ApiKey(uid);

    when(session.load(ApiKey.class, uid)).thenReturn(a);
    doNothing().when(session).delete(a);
    var actual = dao.delete(uid);
    assertTrue(actual);
  }

  @Test
  public void deleteTest_Error() {
    var uid = UUID.randomUUID();
    var a = new ApiKey(uid);

    when(session.load(ApiKey.class, uid)).thenReturn(null);
    doNothing().when(session).delete(a);
    var actual = dao.delete(uid);
    assertFalse(actual);
  }
}
