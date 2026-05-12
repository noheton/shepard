package de.dlr.shepard.auth.permission.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.permission.daos.PermissionsDAO;
import de.dlr.shepard.auth.permission.events.PermissionsChangedEvent;
import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.data.file.entities.FileContainer;
import jakarta.enterprise.event.Event;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * A5b — coverage that {@link PermissionsService} fires
 * {@link PermissionsChangedEvent} on every write-path.
 */
public class PermissionsServiceEventTest extends BaseTestCase {

  @Mock
  private UserService userService;

  @Mock
  private UserGroupService userGroupService;

  @Mock
  private PermissionsDAO dao;

  @SuppressWarnings("unchecked")
  @Mock
  private Event<PermissionsChangedEvent> permissionsChangedEvent;

  @InjectMocks
  private PermissionsService service;

  @Test
  public void createPermissions_firesCreatedEvent() {
    var entity = new FileContainer(99L);
    entity.setAppId("file-99");
    var owner = new User("alice");
    when(dao.createOrUpdate(any(Permissions.class))).thenReturn(new Permissions(entity, owner, PermissionType.Private));

    service.createPermissions(entity, owner, PermissionType.Private);

    ArgumentCaptor<PermissionsChangedEvent> cap = ArgumentCaptor.forClass(PermissionsChangedEvent.class);
    verify(permissionsChangedEvent).fire(cap.capture());
    PermissionsChangedEvent fired = cap.getValue();
    assertNotNull(fired);
    assertEquals(99L, fired.getEntityId());
    assertEquals("FileContainer", fired.getEntityKind());
    assertEquals("file-99", fired.getEntityAppId());
    assertEquals(PermissionsChangedEvent.Kind.CREATED, fired.getKind());
  }

  @Test
  public void updatePermissions_firesUpdatedEvent() {
    var owner = new User("owner");
    var reader = new User("reader");
    var con = new FileContainer(2L);
    con.setAppId("file-2");

    ArrayList<BasicEntity> conList = new ArrayList<>();
    conList.add(con);

    var existing = new Permissions(1L);
    existing.setEntities(conList);
    existing.setOwner(owner);
    con.setPermissions(existing);

    var io = new PermissionsIO() {
      {
        setOwner("owner");
        setReader(new String[] { "reader" });
        setWriter(new String[0]);
        setManager(new String[0]);
      }
    };

    when(userService.getCurrentUser()).thenReturn(owner);
    when(userService.getUserOptional("owner")).thenReturn(Optional.of(owner));
    when(userService.getUserOptional("reader")).thenReturn(Optional.of(reader));
    when(dao.findByEntityNeo4jId(2L)).thenReturn(existing);
    when(dao.createOrUpdate(any(Permissions.class))).thenReturn(existing);

    service.updatePermissionsByNeo4jId(io, 2L);

    ArgumentCaptor<PermissionsChangedEvent> cap = ArgumentCaptor.forClass(PermissionsChangedEvent.class);
    verify(permissionsChangedEvent, atLeastOnce()).fire(cap.capture());
    // The update path also re-fetches via the entity-id helper; we don't constrain to exactly one fire.
    boolean sawUpdate = cap.getAllValues().stream().anyMatch(e -> e.getKind() == PermissionsChangedEvent.Kind.UPDATED && e.getEntityId() == 2L);
    assertEquals(true, sawUpdate, "expected UPDATED event for entityId=2");
  }

  @Test
  public void deletePermissions_firesDeletedEvent() {
    var con = new FileContainer(3L);
    con.setAppId("file-3");
    ArrayList<BasicEntity> conList = new ArrayList<>();
    conList.add(con);

    var perms = new Permissions(7L);
    perms.setEntities(conList);

    when(dao.deleteByNeo4jId(7L)).thenReturn(true);

    service.deletePermissions(perms);

    ArgumentCaptor<PermissionsChangedEvent> cap = ArgumentCaptor.forClass(PermissionsChangedEvent.class);
    verify(permissionsChangedEvent).fire(cap.capture());
    var fired = cap.getValue();
    assertEquals(3L, fired.getEntityId());
    assertEquals("FileContainer", fired.getEntityKind());
    assertEquals("file-3", fired.getEntityAppId());
    assertEquals(PermissionsChangedEvent.Kind.DELETED, fired.getKind());
  }

  @Test
  public void deletePermissions_skipsEventOnFailure() {
    var con = new FileContainer(4L);
    ArrayList<BasicEntity> conList = new ArrayList<>();
    conList.add(con);
    var perms = new Permissions(8L);
    perms.setEntities(conList);

    when(dao.deleteByNeo4jId(8L)).thenReturn(false);

    service.deletePermissions(perms);

    verify(permissionsChangedEvent, times(0)).fire(any());
  }

  @Test
  public void deletePermissions_nullSafe() {
    // No event fired for null input.
    service.deletePermissions(null);
    verify(permissionsChangedEvent, times(0)).fire(any());
  }
}
