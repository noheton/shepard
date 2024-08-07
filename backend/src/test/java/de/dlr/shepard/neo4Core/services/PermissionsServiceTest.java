package de.dlr.shepard.neo4Core.services;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.UserGroupDAO;
import de.dlr.shepard.neo4Core.entities.BasicEntity;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PermissionsServiceTest extends BaseTestCase {

  @Mock
  private UserDAO userDAO;

  @Mock
  private UserGroupDAO userGroupDAO;

  @Mock
  private PermissionsDAO dao;

  @InjectMocks
  private PermissionsService service;

  @Test
  public void createPermissionsTest() {
    var con = new FileContainer(2L);
    ArrayList<BasicEntity> conList = new ArrayList<BasicEntity>();
    conList.add(con);
    var perms = new Permissions();

    var created = new Permissions(1L);
    created.setEntities(conList);

    when(dao.createWithEntityNeo4jId(perms, 2L)).thenReturn(created);

    var actual = service.createPermissionsByNeo4jId(2L);
    assertEquals(created, actual);
  }

  @Test
  public void getPermissionsByNeo4jIdTest() {
    var con = new FileContainer(2L);
    ArrayList<BasicEntity> conList = new ArrayList<BasicEntity>();
    conList.add(con);
    var perms = new Permissions();
    perms.setEntities(conList);

    when(dao.findByEntityNeo4jId(2L)).thenReturn(perms);
    var actual = service.getPermissionsByNeo4jId(2L);
    assertEquals(perms, actual);
  }

  @Test
  public void getPermissionsByShepardIdTest() {
    var col = new Collection(2L);
    col.setShepardId(3L);
    ArrayList<BasicEntity> colList = new ArrayList<BasicEntity>();
    var perms = new Permissions(1L);
    perms.setEntities(colList);

    when(dao.findByEntityShepardId(col.getShepardId())).thenReturn(perms);
    var actual = service.getPermissionsByShepardId(col.getShepardId());
    assertEquals(perms, actual);
  }

  @Test
  public void getPermissionsByCollectionShepardIdTest() {
    var col = new Collection(2L);
    col.setShepardId(3L);
    ArrayList<BasicEntity> colList = new ArrayList<BasicEntity>();
    var perms = new Permissions(1L);
    perms.setEntities(colList);

    when(dao.findByCollectionShepardId(col.getShepardId())).thenReturn(perms);
    var actual = service.getPermissionsByCollectionShepardId(col.getShepardId());
    assertEquals(perms, actual);
  }

  @Test
  public void getPermissionsByNeo4jIdTest_notFound() {
    when(dao.findByEntityNeo4jId(2L)).thenReturn(null);

    var actual = service.getPermissionsByNeo4jId(2L);
    assertNull(actual);
    verify(dao).findByEntityNeo4jId(2L);
  }

  @Test
  public void getPermissionsByShepardIdTest_notFound() {
    when(dao.findByEntityShepardId(2L)).thenReturn(null);

    var actual = service.getPermissionsByShepardId(2L);
    assertNull(actual);
    verify(dao).findByEntityShepardId(2L);
  }

  @Test
  public void getPermissionsByCollectionShepardIdTest_notFound() {
    when(dao.findByCollectionShepardId(2L)).thenReturn(null);

    var actual = service.getPermissionsByCollectionShepardId(2L);
    assertNull(actual);
    verify(dao).findByCollectionShepardId(2L);
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

    when(userDAO.find("owner")).thenReturn(owner);
    when(userDAO.find("reader")).thenReturn(reader);
    when(userDAO.find("writer")).thenReturn(writer);
    when(userDAO.find("manager")).thenReturn(manager);
    when(userGroupDAO.findByNeo4jId(12L)).thenReturn(writerGroup);
    when(dao.findByEntityNeo4jId(2L)).thenReturn(existing);
    when(dao.createOrUpdate(updated)).thenReturn(updated);
    var actual = service.updatePermissionsByNeo4jId(perms, 2L);
    assertEquals(updated, actual);
  }

  @Test
  public void updatePermissionsByShepardIdTest() {
    var owner = new User("owner");
    var reader = new User("reader");
    var writer = new User("writer");
    var manager = new User("manager");
    List<User> writerGroupList = List.of(new User("groupwriter"));
    UserGroup writerGroup = new UserGroup(12L);
    writerGroup.setName("writerGroup");
    writerGroup.setUsers(writerGroupList);
    List<UserGroup> writerGroupsList = List.of(writerGroup);

    var col = new Collection(2L);
    col.setShepardId(4L);
    ArrayList<BasicEntity> colList = new ArrayList<BasicEntity>();
    colList.add(col);
    var existing = new Permissions(1L);
    existing.setEntities(colList);

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
        setEntities(colList);
        setOwner(owner);
        setReader(List.of(reader));
        setWriter(List.of(writer));
        setWriterGroups(writerGroupsList);
        setManager(List.of(manager));
      }
    };

    when(userDAO.find("owner")).thenReturn(owner);
    when(userDAO.find("reader")).thenReturn(reader);
    when(userDAO.find("writer")).thenReturn(writer);
    when(userDAO.find("manager")).thenReturn(manager);
    when(userGroupDAO.findByNeo4jId(12L)).thenReturn(writerGroup);
    when(dao.findByEntityShepardId(col.getShepardId())).thenReturn(existing);
    when(dao.createOrUpdate(updated)).thenReturn(updated);
    var actual = service.updatePermissionsByShepardId(perms, col.getShepardId());
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

    when(userDAO.find("owner")).thenReturn(owner);
    when(userDAO.find("reader")).thenReturn(reader);
    when(userDAO.find("writer")).thenReturn(writer);
    when(userDAO.find("manager")).thenReturn(manager);
    when(dao.findByEntityNeo4jId(2L)).thenReturn(null);
    when(dao.createWithEntityNeo4jId(toCreate, 2L)).thenReturn(updated);

    var actual = service.updatePermissionsByNeo4jId(perms, 2L);
    assertEquals(updated, actual);
  }

  @Test
  public void updatePermissionsByShepardIdTest_oldIsNull() {
    var owner = new User("owner");
    var reader = new User("reader");
    var writer = new User("writer");
    var manager = new User("manager");

    var col = new Collection(2L);
    col.setShepardId(4L);
    ArrayList<BasicEntity> colList = new ArrayList<BasicEntity>();
    colList.add(col);
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
        setEntities(colList);
        setOwner(owner);
        setReader(List.of(reader));
        setWriter(List.of(writer));
        setReaderGroups(Collections.emptyList());
        setWriterGroups(Collections.emptyList());
        setManager(List.of(manager));
      }
    };

    when(userDAO.find("owner")).thenReturn(owner);
    when(userDAO.find("reader")).thenReturn(reader);
    when(userDAO.find("writer")).thenReturn(writer);
    when(userDAO.find("manager")).thenReturn(manager);
    when(dao.findByEntityShepardId(col.getShepardId())).thenReturn(null);
    when(dao.createWithEntityShepardId(toCreate, col.getShepardId())).thenReturn(updated);

    var actual = service.updatePermissionsByShepardId(perms, col.getShepardId());
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

    when(userDAO.find("reader")).thenReturn(reader);
    when(userDAO.find("writer")).thenReturn(writer);
    when(userDAO.find("manager")).thenReturn(manager);
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

    when(userDAO.find("reader")).thenReturn(reader);
    when(userDAO.find("writer")).thenReturn(writer);
    when(userDAO.find("manager")).thenReturn(manager);
    when(dao.findByEntityNeo4jId(col.getShepardId())).thenReturn(existing);
    when(dao.createOrUpdate(updated)).thenReturn(updated);

    var actual = service.updatePermissionsByNeo4jId(perms, col.getShepardId());
    assertEquals(updated, actual);
  }
}
