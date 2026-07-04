package de.dlr.shepard.v2.admin.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.role.daos.RoleDAO;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.Constants;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class InstanceAdminServiceTest extends BaseTestCase {

  @Mock
  RoleDAO roleDAO;

  @Mock
  UserDAO userDAO;

  @InjectMocks
  InstanceAdminService service;

  @Test
  void listInstanceAdmins_translatesAuditRows() {
    when(roleDAO.listGrants(Constants.INSTANCE_ADMIN_ROLE)).thenReturn(
      List.of(
        new RoleDAO.RoleGrant("alice", "bootstrap", 1_700_000_000_000L),
        new RoleDAO.RoleGrant("bob", "alice", 1_710_000_000_000L)
      )
    );
    var rows = service.listInstanceAdmins();
    assertEquals(2, rows.size());
    assertEquals("alice", rows.get(0).getUsername());
    assertEquals("Neo4j", rows.get(0).getSource());
    assertEquals("bootstrap", rows.get(0).getGrantedBy());
  }

  @Test
  void grantInstanceAdmin_unknownUser_rejected() {
    when(userDAO.find("ghost")).thenReturn(null);
    assertThrows(InvalidPathException.class, () -> service.grantInstanceAdmin("ghost", "admin"));
  }

  @Test
  void grantInstanceAdmin_happyPath_callsDao() {
    when(userDAO.find("alice")).thenReturn(new User("alice"));
    when(roleDAO.grantRole(anyString(), anyString(), anyString(), anyLong())).thenReturn(true);
    var grant = service.grantInstanceAdmin("alice", "admin");
    assertEquals("alice", grant.getUsername());
    assertEquals("Neo4j", grant.getSource());
    assertEquals("admin", grant.getGrantedBy());
  }

  @Test
  void revokeInstanceAdmin_returnsFalseWhenNoEdge() {
    when(roleDAO.revokeRole("ghost", Constants.INSTANCE_ADMIN_ROLE)).thenReturn(false);
    org.junit.jupiter.api.Assertions.assertFalse(service.revokeInstanceAdmin("ghost"));
  }

  @Test
  void revokeInstanceAdmin_returnsTrueWhenEdgeDeleted() {
    when(roleDAO.revokeRole("alice", Constants.INSTANCE_ADMIN_ROLE)).thenReturn(true);
    assertTrue(service.revokeInstanceAdmin("alice"));
  }

  @Test
  void grantInstanceAdmin_stampsRoleChangedAt() {
    // ROLE-GRANT-STALE-SESSION-02 — on successful grant the affected
    // :User node gets a fresh `roleChangedAt` so subsequent JWT validation
    // rejects any session predating this moment.
    User u = new User("alice");
    when(userDAO.find("alice")).thenReturn(u);
    when(roleDAO.grantRole(anyString(), anyString(), anyString(), anyLong())).thenReturn(true);

    long before = System.currentTimeMillis();
    service.grantInstanceAdmin("alice", "admin");
    long after = System.currentTimeMillis();

    assertNotNull(u.getRoleChangedAt(), "grant must stamp roleChangedAt");
    long stamped = u.getRoleChangedAt().getTime();
    assertTrue(stamped >= before && stamped <= after, "stamp must be inside the call window");
    verify(userDAO).createOrUpdate(u);
  }

  @Test
  void revokeInstanceAdmin_stampsRoleChangedAtWhenEdgeDeleted() {
    User u = new User("alice");
    when(userDAO.find("alice")).thenReturn(u);
    when(roleDAO.revokeRole("alice", Constants.INSTANCE_ADMIN_ROLE)).thenReturn(true);

    assertTrue(service.revokeInstanceAdmin("alice"));
    assertNotNull(u.getRoleChangedAt(), "revoke must stamp roleChangedAt on success");
    verify(userDAO).createOrUpdate(u);
  }

  @Test
  void revokeInstanceAdmin_doesNotStampWhenNoEdge() {
    // Revoke that doesn't delete an edge (user never had the role) must
    // not invalidate the user's active session.
    when(roleDAO.revokeRole("ghost", Constants.INSTANCE_ADMIN_ROLE)).thenReturn(false);
    org.junit.jupiter.api.Assertions.assertFalse(service.revokeInstanceAdmin("ghost"));
    verify(userDAO, never()).createOrUpdate(any(User.class));
  }

  @Test
  void rolesForUser_combinesIdpAndNeo4j() {
    service.instanceAdminClaimValueOpt = Optional.of("shepard-admin");
    when(roleDAO.rolesForUser("alice")).thenReturn(List.of("instance-admin"));
    var roles = service.rolesForUser("alice", List.of("shepard-admin"));
    assertEquals(1, roles.size());
    assertTrue(roles.contains("instance-admin"));
  }
}
