package de.dlr.shepard.context.collection.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.references.basicreference.daos.BasicReferenceDAO;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.entities.Version;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class CollectionServiceTest {

  @InjectMock
  CollectionDAO dao;

  @InjectMock
  VersionDAO versionDAO;

  @InjectMock
  BasicReferenceDAO referenceDAO;

  @InjectMock
  UserService userService;

  @InjectMock
  AuthenticationContext authenticationContext;

  @InjectMock
  PermissionsService permissionsService;

  @InjectMock
  DateHelper dateHelper;

  @Inject
  CollectionService service;

  @Test
  public void getCollectionsByShepardIdTest() {
    String username = "manni";
    Collection collectionNotDeleted = new Collection(5L);
    collectionNotDeleted.setShepardId(55L);
    Collection collectionDeleted = new Collection(6L);
    collectionDeleted.setShepardId(65L);
    collectionDeleted.setDeleted(true);

    when(authenticationContext.getCurrentUserName()).thenReturn("manni");
    when(dao.findAllCollectionsByShepardId(null, username)).thenReturn(List.of(collectionNotDeleted));
    List<Collection> returned = service.getAllCollections(null);
    assertEquals(List.of(collectionNotDeleted), returned);
  }

  @Test
  public void getCollectionsByShepardIdTest_withName() {
    String username = "kurac";
    Collection collectionNotDeleted = new Collection(5L);
    collectionNotDeleted.setShepardId(55L);
    Collection collectionDeleted = new Collection(6L);
    collectionDeleted.setShepardId(65L);
    collectionDeleted.setDeleted(true);

    QueryParamHelper params = new QueryParamHelper().withName("test");
    when(authenticationContext.getCurrentUserName()).thenReturn("kurac");
    when(dao.findAllCollectionsByShepardId(params, username)).thenReturn(List.of(collectionNotDeleted));
    List<Collection> returned = service.getAllCollections(params);
    assertEquals(List.of(collectionNotDeleted), returned);
  }

  @Test
  public void createCollectionTest() {
    User user = new User("bob");
    Date date = new Date(23);
    Version nullVersion = new Version(new UUID(1L, 2L));
    CollectionIO input = new CollectionIO() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
      }
    };
    Collection toCreate = new Collection() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(user);
      }
    };
    Collection created = new Collection() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(user);
        setId(1L);
      }
    };
    Collection createdWithShepardId = new Collection() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(user);
        setId(created.getId());
        setShepardId(created.getId());
      }
    };
    createdWithShepardId.setVersion(nullVersion);
    when(userService.getCurrentUser()).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(versionDAO.createOrUpdate(any())).thenReturn(nullVersion);
    Collection actual = service.createCollection(input);
    assertEquals(createdWithShepardId, actual);
    verify(permissionsService).createPermissions(created, user, PermissionType.Private);
  }

  @Test
  public void updateCollectionByShepardIdTest() {
    User user = new User("bob");
    Date date = new Date(23);
    User updateUser = new User("claus");
    Date updateDate = new Date(43);

    CollectionIO input = new CollectionIO() {
      {
        setId(1L);
        setAttributes(Map.of("1", "2", "c", "d"));
        setDescription("newDesc");
        setName("newName");
      }
    };
    Collection old = new Collection() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(user);
        setId(15L);
        setShepardId(input.getId());
      }
    };
    var updated = new Collection() {
      {
        setAttributes(Map.of("1", "2", "c", "d"));
        setDescription("newDesc");
        setName("newName");
        setCreatedAt(date);
        setCreatedBy(user);
        setUpdatedAt(updateDate);
        setUpdatedBy(updateUser);
        setId(old.getId());
        setShepardId(old.getShepardId());
      }
    };

    when(dao.findByShepardId(old.getShepardId(), false)).thenReturn(old);
    when(userService.getCurrentUser()).thenReturn(updateUser);
    when(dateHelper.getDate()).thenReturn(updateDate);
    when(dao.createOrUpdate(updated)).thenReturn(updated);
    when(authenticationContext.getCurrentUserName()).thenReturn(updateUser.getUsername());

    when(
      permissionsService.isAccessTypeAllowedForUser(old.getShepardId(), AccessType.Read, updateUser.getUsername())
    ).thenReturn(true);
    when(
      permissionsService.isAccessTypeAllowedForUser(old.getShepardId(), AccessType.Write, updateUser.getUsername())
    ).thenReturn(true);

    var actual = service.updateCollectionByShepardId(old.getShepardId(), input);
    assertEquals(updated, actual);
  }

  @Test
  public void updateCollectionByShepardIdTest_noUpdatePermissions() {
    User user = new User("bob");
    Date date = new Date(23);
    User updateUser = new User("claus");
    Date updateDate = new Date(43);

    CollectionIO input = new CollectionIO() {
      {
        setId(1L);
        setAttributes(Map.of("1", "2", "c", "d"));
        setDescription("newDesc");
        setName("newName");
      }
    };
    Collection old = new Collection() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(user);
        setId(15L);
        setShepardId(input.getId());
      }
    };
    var updated = new Collection() {
      {
        setAttributes(Map.of("1", "2", "c", "d"));
        setDescription("newDesc");
        setName("newName");
        setCreatedAt(date);
        setCreatedBy(user);
        setUpdatedAt(updateDate);
        setUpdatedBy(updateUser);
        setId(old.getId());
        setShepardId(old.getShepardId());
      }
    };

    when(dao.findByShepardId(old.getShepardId(), false)).thenReturn(old);
    when(userService.getCurrentUser()).thenReturn(updateUser);
    when(dateHelper.getDate()).thenReturn(updateDate);
    when(dao.createOrUpdate(updated)).thenReturn(updated);
    when(authenticationContext.getCurrentUserName()).thenReturn(updateUser.getUsername());

    when(
      permissionsService.isAccessTypeAllowedForUser(old.getShepardId(), AccessType.Read, updateUser.getUsername())
    ).thenReturn(true);
    when(
      permissionsService.isAccessTypeAllowedForUser(old.getShepardId(), AccessType.Write, updateUser.getUsername())
    ).thenReturn(false);

    assertThrows(InvalidAuthException.class, () -> service.updateCollectionByShepardId(old.getShepardId(), input));
  }

  @Test
  public void deleteCollectionByShepardIdTest() {
    User user = new User("bob");
    Date date = new Date(23);

    Collection collection = new Collection(1L);
    collection.setShepardId(15L);

    when(userService.getCurrentUser()).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(dao.deleteCollectionByShepardId(collection.getShepardId(), user, date)).thenReturn(true);
    when(dao.findByShepardId(collection.getShepardId(), true)).thenReturn(collection);
    when(authenticationContext.getCurrentUserName()).thenReturn("bob");

    when(permissionsService.isAccessTypeAllowedForUser(collection.getShepardId(), AccessType.Read, "bob")).thenReturn(
      true
    );
    when(permissionsService.isAccessTypeAllowedForUser(collection.getShepardId(), AccessType.Write, "bob")).thenReturn(
      true
    );

    assertDoesNotThrow(() -> service.deleteCollection(collection.getShepardId()));
  }

  @Test
  public void deleteCollectionByShepardIdTestNoUpdatePermissions() {
    User user = new User("timbo");
    Date date = new Date(23);

    Collection collection = new Collection(1L);
    collection.setShepardId(15L);

    when(userService.getCurrentUser()).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(dao.deleteCollectionByShepardId(collection.getShepardId(), user, date)).thenReturn(true);
    when(dao.findByShepardId(collection.getShepardId(), true)).thenReturn(collection);
    when(authenticationContext.getCurrentUserName()).thenReturn("timbo");

    when(permissionsService.isAccessTypeAllowedForUser(collection.getShepardId(), AccessType.Read, "timbo")).thenReturn(
      true
    );
    when(
      permissionsService.isAccessTypeAllowedForUser(collection.getShepardId(), AccessType.Write, "timbo")
    ).thenReturn(false);

    assertThrows(InvalidAuthException.class, () -> service.deleteCollection(collection.getShepardId()));
  }

  @Test
  public void getCollectionByShepardIdNoVersion() {
    Collection ret = new Collection(1L);
    long shepardId = 2L;
    when(dao.findByShepardId(shepardId, false)).thenReturn(ret);
    when(authenticationContext.getCurrentUserName()).thenReturn("bob");
    when(permissionsService.isAccessTypeAllowedForUser(shepardId, AccessType.Read, "bob")).thenReturn(true);
    var result = service.getCollectionWithDataObjectsAndIncomingReferences(shepardId);
    assertEquals(ret, result);
  }

  @Test
  public void getCollectionByShepardIdNoVersionNotFound() {
    long shepardId = 2L;
    when(dao.findByShepardId(shepardId, false)).thenReturn(null);

    assertThrows(InvalidPathException.class, () -> {
      service.getCollectionWithDataObjectsAndIncomingReferences(shepardId, null);
    });
  }

  @Test
  public void getCollectionByShepardIdNoVersionDeleted() {
    Collection ret = new Collection(1L);
    ret.setDeleted(true);
    long shepardId = 2L;
    when(dao.findByShepardId(shepardId)).thenReturn(ret);

    assertThrows(InvalidPathException.class, () -> {
      service.getCollectionWithDataObjectsAndIncomingReferences(shepardId, null);
    });
  }

  public void getCollectionByShepardIdNoVersionNoReadPermissions() {
    Collection ret = new Collection(1L);
    long shepardId = 2L;
    when(dao.findByShepardId(shepardId, false)).thenReturn(ret);
    when(authenticationContext.getCurrentUserName()).thenReturn("eric");
    when(permissionsService.isAccessTypeAllowedForUser(shepardId, AccessType.Read, "eric")).thenReturn(false);
    assertThrows(InvalidAuthException.class, () -> service.getCollectionWithDataObjectsAndIncomingReferences(shepardId)
    );
  }

  @Test
  public void getCollectionByShepardId() {
    Collection ret = new Collection(1L);
    UUID versionUID = new UUID(1L, 2L);
    long shepardId = 2L;
    when(dao.findByShepardId(shepardId, versionUID, false)).thenReturn(ret);
    when(authenticationContext.getCurrentUserName()).thenReturn("bob");
    when(permissionsService.isAccessTypeAllowedForUser(shepardId, AccessType.Read, "bob")).thenReturn(true);
    var result = service.getCollectionWithDataObjectsAndIncomingReferences(shepardId, versionUID);
    assertEquals(ret, result);
  }

  @Test
  public void getCollectionByShepardIdNotFound() {
    UUID versionUID = new UUID(1L, 2L);
    long shepardId = 2L;
    when(dao.findByShepardId(shepardId, versionUID)).thenReturn(null);

    assertThrows(InvalidPathException.class, () -> {
      service.getCollectionWithDataObjectsAndIncomingReferences(shepardId, versionUID);
    });
  }

  @Test
  public void getCollectionByShepardIdDeleted() {
    Collection ret = new Collection(1L);
    ret.setDeleted(true);
    UUID versionUID = new UUID(1L, 2L);
    long shepardId = 2L;
    when(dao.findByShepardId(shepardId, versionUID)).thenReturn(ret);
    assertThrows(InvalidPathException.class, () -> {
      service.getCollectionWithDataObjectsAndIncomingReferences(shepardId, versionUID);
    });
  }

  @Test
  public void getCollectionPermissions() {
    Collection col = new Collection(1L);
    Permissions ret = new Permissions(col, new User("bob"), PermissionType.Private);
    col.setPermissions(ret);
    long shepardId = 2L;

    when(dao.findByShepardId(shepardId, true)).thenReturn(col);
    when(authenticationContext.getCurrentUserName()).thenReturn("bob");
    when(permissionsService.isAccessTypeAllowedForUser(shepardId, AccessType.Read, "bob")).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(shepardId, AccessType.Manage, "bob")).thenReturn(true);
    when(permissionsService.getPermissionsOfEntity(shepardId)).thenReturn(ret);

    var result = service.getCollectionPermissions(shepardId);
    assertEquals(ret, result);
  }

  @Test
  public void getCollectionPermissionsNoManagePermissions() {
    Collection col = new Collection(1L);
    Permissions ret = new Permissions(col, new User("bob"), PermissionType.Private);
    col.setPermissions(ret);
    long shepardId = 2L;
    when(dao.findByShepardId(shepardId, true)).thenReturn(col);
    when(authenticationContext.getCurrentUserName()).thenReturn("bob");
    when(permissionsService.isAccessTypeAllowedForUser(shepardId, AccessType.Read, "bob")).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(shepardId, AccessType.Manage, "bob")).thenReturn(false);
    when(permissionsService.getPermissionsOfEntityOptional(shepardId)).thenReturn(Optional.of(ret));
    assertThrows(InvalidAuthException.class, () -> service.getCollectionPermissions(shepardId));
  }

  @Test
  public void updateCollectionPermissions() {
    Collection col = new Collection(1L);
    long shepardId = 2L;
    col.setShepardId(shepardId);
    Permissions newPermissions = new Permissions(col, new User("bob"), PermissionType.Public);
    PermissionsIO permissionsIO = new PermissionsIO(newPermissions);
    when(dao.findByShepardId(shepardId, true)).thenReturn(col);
    when(authenticationContext.getCurrentUserName()).thenReturn("bob");
    when(permissionsService.isAccessTypeAllowedForUser(shepardId, AccessType.Read, "bob")).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(shepardId, AccessType.Manage, "bob")).thenReturn(true);
    when(permissionsService.updatePermissionsByNeo4jId(permissionsIO, shepardId)).thenReturn(newPermissions);
    var result = service.updateCollectionPermissions(permissionsIO, shepardId);
    assertEquals(newPermissions, result);
  }

  @Test
  public void updateCollectionPermissionsNoManagePermissions() {
    Collection col = new Collection(1L);
    long shepardId = 2L;
    col.setShepardId(shepardId);
    Permissions newPermissions = new Permissions(col, new User("bob"), PermissionType.Public);
    PermissionsIO permissionsIO = new PermissionsIO(newPermissions);
    when(dao.findByShepardId(shepardId, true)).thenReturn(col);
    when(authenticationContext.getCurrentUserName()).thenReturn("bob");
    when(permissionsService.isAccessTypeAllowedForUser(shepardId, AccessType.Read, "bob")).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(shepardId, AccessType.Manage, "bob")).thenReturn(false);
    when(permissionsService.updatePermissionsByNeo4jId(permissionsIO, shepardId)).thenReturn(newPermissions);
    assertThrows(InvalidAuthException.class, () -> service.updateCollectionPermissions(permissionsIO, shepardId));
  }
}
