package de.dlr.shepard.v2.admin.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.bootstrap.BootstrapState;
import de.dlr.shepard.auth.bootstrap.BootstrapStateDAO;
import de.dlr.shepard.auth.bootstrap.BootstrapTokenInitializer;
import de.dlr.shepard.auth.role.daos.RoleDAO;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.Constants;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class BootstrapServiceTest extends BaseTestCase {

  @Mock
  BootstrapStateDAO bootstrapStateDAO;

  @Mock
  RoleDAO roleDAO;

  @Mock
  UserDAO userDAO;

  @InjectMocks
  BootstrapService service;

  @Test
  void consumeBootstrap_happyPath_grantsRoleAndDeletesState() {
    String token = "abc-123";
    String hash = BootstrapTokenInitializer.sha256Hex(token);
    var state = new BootstrapState(hash, new Date());
    when(bootstrapStateDAO.findOne()).thenReturn(Optional.of(state));
    when(userDAO.find("alice")).thenReturn(new User("alice"));
    when(roleDAO.grantRole(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);

    String granted = service.consumeBootstrap(token, "alice");

    assertEquals("alice", granted);
    verify(roleDAO).ensureRole(Constants.INSTANCE_ADMIN_ROLE, Constants.INSTANCE_ADMIN_DISPLAY_NAME);
    verify(roleDAO).grantRole(
      org.mockito.ArgumentMatchers.eq("alice"),
      org.mockito.ArgumentMatchers.eq(Constants.INSTANCE_ADMIN_ROLE),
      org.mockito.ArgumentMatchers.eq(Constants.BOOTSTRAP_GRANTER),
      org.mockito.ArgumentMatchers.anyLong()
    );
    verify(bootstrapStateDAO).deleteAll();
  }

  @Test
  void consumeBootstrap_replayed_secondCallRejected() {
    // First call OK.
    String token = "real-token";
    String hash = BootstrapTokenInitializer.sha256Hex(token);
    var state = new BootstrapState(hash, new Date());
    when(bootstrapStateDAO.findOne()).thenReturn(Optional.of(state)).thenReturn(Optional.empty());
    when(userDAO.find("alice")).thenReturn(new User("alice"));
    when(roleDAO.grantRole(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);

    service.consumeBootstrap(token, "alice");
    // Second call: state node is gone -> 403.
    assertThrows(InvalidAuthException.class, () -> service.consumeBootstrap(token, "alice"));
    verify(bootstrapStateDAO, times(2)).findOne();
  }

  @Test
  void consumeBootstrap_wrongToken_rejected() {
    String hash = BootstrapTokenInitializer.sha256Hex("real-token");
    var state = new BootstrapState(hash, new Date());
    when(bootstrapStateDAO.findOne()).thenReturn(Optional.of(state));
    assertThrows(InvalidAuthException.class, () -> service.consumeBootstrap("forged-token", "alice"));
    verify(bootstrapStateDAO, times(0)).deleteAll();
  }

  @Test
  void consumeBootstrap_emptyArgs_rejected() {
    assertThrows(InvalidRequestException.class, () -> service.consumeBootstrap("", "alice"));
    assertThrows(InvalidRequestException.class, () -> service.consumeBootstrap("token", " "));
  }

  @Test
  void consumeBootstrap_unknownUser_rejected() {
    String token = "t";
    String hash = BootstrapTokenInitializer.sha256Hex(token);
    when(bootstrapStateDAO.findOne()).thenReturn(Optional.of(new BootstrapState(hash, new Date())));
    when(userDAO.find("ghost")).thenReturn(null);
    assertThrows(InvalidPathException.class, () -> service.consumeBootstrap(token, "ghost"));
  }
}
