package de.dlr.shepard.auth.permission.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.permission.daos.PermissionsDAO;
import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.data.file.entities.FileContainer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PermissionsServiceTest extends BaseTestCase {

  @Mock
  private UserService userService;

  @Mock
  private UserGroupService userGroupService;

  @Mock
  private PermissionsDAO dao;

  @InjectMocks
  private PermissionsService service;

  @Test
  public void createPermissions_callsDAO() {
    var entity = new BasicEntity(2L);
    var user = new User("name");
    var permissionType = PermissionType.Private;
    service.createPermissions(entity, user, permissionType);
    verify(dao).createOrUpdate(new Permissions(entity, user, permissionType));
  }

  @Test
  public void getPermissionsByNeo4jIdTest() {
    var con = new FileContainer(2L);
    ArrayList<BasicEntity> conList = new ArrayList<BasicEntity>();
    conList.add(con);
    var perms = new Permissions();
    perms.setEntities(conList);

    when(dao.findByEntityNeo4jId(2L)).thenReturn(perms);
    var actual = service.getPermissionsOfEntity(2L);
    assertEquals(perms, actual);
  }

  @Test
  public void getPermissionsByNeo4jIdTest_notFound() {
    when(dao.findByEntityNeo4jId(2L)).thenReturn(null);

    var actual = service.getPermissionsOfEntity(2L);
    assertNull(actual);
    verify(dao).findByEntityNeo4jId(2L);
  }

  @Test
  public void updatePermissionsByNeo4jIdTest() {
    var owner = new User("owner");
    var reader = new User("reader");
    var writer = new User("writer");
    var manager = new User("manager");
    List<User> writerGroupList = List.of(new User("groupwriter"));
    UserGroup writerGroup = new UserGroup(12L);
    writerGroup.setName("writerGroup");
    writerGroup.setUsers(writerGroupList);
    List<UserGroup> writerGroupsList = List.of(writerGroup);

    var con = new FileContainer(2L);
    ArrayList<BasicEntity> conList = new ArrayList<BasicEntity>();
    conList.add(con);
    var existing = new Permissions(1L);
    existing.setEntities(conList);

    var perms = new PermissionsIO() {
      {
        setOwner("owner");
        setReader(new String[] { "reader", "false" });
        setWriter(new String[] { "writer" });
        setWriterGroupIds(new long[] { 12L, -1L });
        setManager(new String[] { "manager" });
      }
    };

    var updated = new Permissions() {
      {
        setId(1L);
        setEntities(conList);
        setOwner(owner);
        setReader(List.of(reader));
        setWriter(List.of(writer));
        setWriterGroups(writerGroupsList);
        setManager(List.of(manager));
      }
    };

    when(userService.getUser("owner")).thenReturn(owner);
    when(userService.getUser("reader")).thenReturn(reader);
    when(userService.getUser("writer")).thenReturn(writer);
    when(userService.getUser("manager")).thenReturn(manager);
    when(userGroupService.getUserGroup(12L)).thenReturn(writerGroup);
    when(dao.findByEntityNeo4jId(2L)).thenReturn(existing);
    when(dao.createOrUpdate(updated)).thenReturn(updated);
    var actual = service.updatePermissionsByNeo4jId(perms, 2L);
    assertEquals(updated, actual);
  }

  @Test
  public void updatePermissionsByNeo4jIdTest_oldIsNull() {
    var owner = new User("owner");
    var reader = new User("reader");
    var writer = new User("writer");
    var manager = new User("manager");

    var con = new FileContainer(2L);
    ArrayList<BasicEntity> conList = new ArrayList<BasicEntity>();
    conList.add(con);
    var perms = new PermissionsIO() {
      {
        setOwner("owner");
        setReader(new String[] { "reader" });
        setWriter(new String[] { "writer" });
        setReaderGroupIds(new long[] {});
        setManager(new String[] { "manager" });
      }
    };

    var toCreate = new Permissions() {
      {
        setEntities(conList);
        setOwner(owner);
        setReader(List.of(reader));
        setWriter(List.of(writer));
        setReaderGroups(Collections.emptyList());
        setWriterGroups(Collections.emptyList());
        setManager(List.of(manager));
      }
    };

    var updated = new Permissions() {
      {
        setId(1L);
        setEntities(conList);
        setOwner(owner);
        setReader(List.of(reader));
        setWriter(List.of(writer));
        setReaderGroups(Collections.emptyList());
        setWriterGroups(Collections.emptyList());
        setManager(List.of(manager));
      }
    };

    when(userService.getUser("owner")).thenReturn(owner);
    when(userService.getUser("reader")).thenReturn(reader);
    when(userService.getUser("writer")).thenReturn(writer);
    when(userService.getUser("manager")).thenReturn(manager);
    when(dao.findByEntityNeo4jId(2L)).thenReturn(null);
    when(dao.createOrUpdate(toCreate)).thenReturn(updated);

    var actual = service.updatePermissionsByNeo4jId(perms, 2L);
    assertEquals(updated, actual);
  }

  @Test
  public void updatePermissionsByNeo4jIdTest_userIsNull() {
    var reader = new User("reader");
    var writer = new User("writer");
    var manager = new User("manager");

    var con = new FileContainer(2L);
    ArrayList<BasicEntity> conList = new ArrayList<BasicEntity>();
    conList.add(con);
    var existing = new Permissions(1L);
    existing.setEntities(conList);

    var perms = new PermissionsIO() {
      {
        setReader(new String[] { "reader", "not_existing" });
        setWriter(new String[] { "writer", null });
        setReaderGroupIds(new long[] {});
        setWriterGroupIds(new long[] {});
        setManager(new String[0]);
      }
    };

    var updated = new Permissions() {
      {
        setId(1L);
        setEntities(conList);
        setReader(List.of(reader));
        setWriter(List.of(writer));
        setReaderGroups(Collections.emptyList());
        setWriterGroups(Collections.emptyList());
      }
    };

    when(userService.getUser("reader")).thenReturn(reader);
    when(userService.getUser("writer")).thenReturn(writer);
    when(userService.getUser("manager")).thenReturn(manager);
    when(dao.findByEntityNeo4jId(2L)).thenReturn(existing);
    when(dao.createOrUpdate(updated)).thenReturn(updated);

    var actual = service.updatePermissionsByNeo4jId(perms, 2L);
    assertEquals(updated, actual);
  }

  @Test
  public void updatePermissionsByShepardIdTest_userIsNull() {
    var reader = new User("reader");
    var writer = new User("writer");
    var manager = new User("manager");

    var col = new Collection(2L);
    col.setShepardId(4L);
    ArrayList<BasicEntity> colList = new ArrayList<BasicEntity>();
    colList.add(col);
    var existing = new Permissions(1L);
    existing.setEntities(colList);

    var perms = new PermissionsIO() {
      {
        setReader(new String[] { "reader", "not_existing" });
        setWriter(new String[] { "writer", null });
        setReaderGroupIds(new long[] {});
        setWriterGroupIds(new long[] {});
        setManager(new String[0]);
      }
    };

    var updated = new Permissions() {
      {
        setId(1L);
        setEntities(colList);
        setReader(List.of(reader));
        setWriter(List.of(writer));
        setReaderGroups(Collections.emptyList());
        setWriterGroups(Collections.emptyList());
      }
    };

    when(userService.getUser("reader")).thenReturn(reader);
    when(userService.getUser("writer")).thenReturn(writer);
    when(userService.getUser("manager")).thenReturn(manager);
    when(dao.findByEntityNeo4jId(col.getShepardId())).thenReturn(existing);
    when(dao.createOrUpdate(updated)).thenReturn(updated);

    var actual = service.updatePermissionsByNeo4jId(perms, col.getShepardId());
    assertEquals(updated, actual);
  }

  @Test
  public void deletePermissions_callsDAO() {
    var permissions = new Permissions(2L);
    service.deletePermissions(permissions);
    verify(dao).deleteByNeo4jId(2L);
  }
}
