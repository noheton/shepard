package de.dlr.shepard.auth.permission.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.permission.daos.PermissionsDAO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Tests for the backward-compat 3-arg overload of
 * {@link PermissionsService#isAccessTypeAllowedForUser(long, AccessType, String)}.
 */
public class PermissionsServiceBackCompatTest extends BaseTestCase {

  @Mock
  private PermissionsDAO permissionsDAO;

  @Mock
  private UserService userService;

  @Mock
  private UserGroupService userGroupService;

  @Mock
  private EntityIdResolver entityIdResolver;

  private PermissionsService service;

  /**
   * Subclass that overrides isAccessAllowedForDataObjectAppId to avoid touching
   * the static NeoConnector singleton in unit tests.
   */
  private static class StubService extends PermissionsService {

    boolean dataObjectAppIdCalled = false;
    String lastDataObjectAppId = null;
    boolean dataObjectResult = true;

    @Override
    public boolean isAccessAllowedForDataObjectAppId(String dataObjectAppId, AccessType accessType, String username) {
      dataObjectAppIdCalled = true;
      lastDataObjectAppId = dataObjectAppId;
      return dataObjectResult;
    }
  }

  private StubService stub;

  @BeforeEach
  public void buildStub() throws Exception {
    stub = new StubService();
    // Inject the mocks manually into the subclass instance's fields.
    org.apache.commons.lang3.reflect.FieldUtils.writeField(stub, "permissionsDAO", permissionsDAO, true);
    org.apache.commons.lang3.reflect.FieldUtils.writeField(stub, "entityIdResolver", entityIdResolver, true);
    org.apache.commons.lang3.reflect.FieldUtils.writeField(stub, "userService", userService, true);
    org.apache.commons.lang3.reflect.FieldUtils.writeField(stub, "userGroupService", userGroupService, true);
    // service is non-null reference needed to avoid NPE in currentIat()
    org.apache.commons.lang3.reflect.FieldUtils.writeField(stub, "authenticationContext", null, true);
    service = stub;
  }

  @Test
  public void branch1_directPermissionsNode_delegatesToFourArgVersion() {
    User owner = new User("alice");
    var perms = new Permissions();
    perms.setOwner(owner);
    perms.setReader(List.of(owner));
    when(permissionsDAO.findByEntityNeo4jId(100L)).thenReturn(perms);

    boolean result = service.isAccessTypeAllowedForUser(100L, AccessType.Read, "alice");
    assertTrue(result);
  }

  @Test
  public void branch2_noDirectPermissionsNode_delegatesToDataObjectAppIdWalk() {
    when(permissionsDAO.findByEntityNeo4jId(200L)).thenReturn(null);
    when(entityIdResolver.resolveAppId(200L)).thenReturn("app-200");
    stub.dataObjectResult = true;

    boolean result = service.isAccessTypeAllowedForUser(200L, AccessType.Read, "bob");
    assertTrue(result);
    assertTrue(stub.dataObjectAppIdCalled);
    org.junit.jupiter.api.Assertions.assertEquals("app-200", stub.lastDataObjectAppId);
  }

  @Test
  public void branch3_entityIdResolverThrowsNotFoundException_returnsFalse() {
    when(permissionsDAO.findByEntityNeo4jId(300L)).thenReturn(null);
    when(entityIdResolver.resolveAppId(300L)).thenThrow(new NotFoundException("no entity"));

    boolean result = service.isAccessTypeAllowedForUser(300L, AccessType.Read, "carol");
    assertFalse(result);
    assertFalse(stub.dataObjectAppIdCalled);
  }
}
