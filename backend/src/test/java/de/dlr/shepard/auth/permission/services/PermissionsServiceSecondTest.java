package de.dlr.shepard.auth.permission.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.permission.daos.PermissionsDAO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.labJournal.services.LabJournalEntryService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

// TODO: Merge with existing PermissionsServiceTest
public class PermissionsServiceSecondTest extends BaseTestCase {

  @Mock
  UriInfo uriInfo;

  @Mock
  private PathSegment rootSeg;

  @Mock
  private PathSegment idSeg;

  @Mock
  private PathSegment pathSeg;

  @Mock
  private PathSegment thirdSegment;

  @Mock
  private PermissionsDAO permissionsDAO;

  @Mock
  private UserGroupService userGroupService;

  @Mock
  private DataObjectService dataObjectService;

  @Mock
  private LabJournalEntryService labJournalEntryService;

  @InjectMocks
  private PermissionsService permissionsService;

  @Mock
  ContainerRequestContext request;

  @BeforeEach
  public void setUpRequestContext() throws URISyntaxException {
    URI uri = new URI("http://my.url/test/200/sub");
    when(uriInfo.getAbsolutePath()).thenReturn(uri);
    when(rootSeg.getPath()).thenReturn(Constants.COLLECTIONS);
    when(idSeg.getPath()).thenReturn("123");
    when(pathSeg.getPath()).thenReturn(Constants.DATA_OBJECTS);
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg, idSeg, pathSeg));
    when(request.getUriInfo()).thenReturn(uriInfo);
  }

  @Test
  public void isAllowedTest_NoId() {
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg));
    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_EmptyId() {
    when(idSeg.getPath()).thenReturn("");

    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_NonNumericId() {
    when(idSeg.getPath()).thenReturn("abc");

    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_GetUsers() {
    when(rootSeg.getPath()).thenReturn(Constants.USERS);
    when(idSeg.getPath()).thenReturn("abc");
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg, idSeg));
    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_EditUsers() {
    when(rootSeg.getPath()).thenReturn(Constants.USERS);
    when(idSeg.getPath()).thenReturn("abc");
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg, idSeg));
    var actual = permissionsService.isAllowed(request, AccessType.Write, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_SearchUsers() {
    when(rootSeg.getPath()).thenReturn(Constants.SEARCH);
    when(idSeg.getPath()).thenReturn(Constants.USERS);
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg, idSeg));
    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isNotAllowedTest_SearchUsersWrongPath() {
    when(rootSeg.getPath()).thenReturn(Constants.SEARCH);
    when(idSeg.getPath()).thenReturn(Constants.USERS);
    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertFalse(actual);
  }

  @Test
  public void isNotAllowedTest_SearchUsersThreeSegments() {
    when(rootSeg.getPath()).thenReturn(Constants.SEARCH);
    when(idSeg.getPath()).thenReturn(Constants.USERS);
    when(thirdSegment.getPath()).thenReturn("bla");
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg, idSeg, thirdSegment));
    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_ManageYourself() {
    when(rootSeg.getPath()).thenReturn(Constants.USERS);
    when(idSeg.getPath()).thenReturn("principal");

    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_ManageOther() {
    when(rootSeg.getPath()).thenReturn(Constants.USERS);
    when(idSeg.getPath()).thenReturn("different");

    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_NoUsername() {
    var perms = new Permissions() {
      {
        setOwner(new User("principal"));
      }
    };
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);

    var actual = permissionsService.isAllowed(request, AccessType.Read, "");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_NoPermissions() {
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(null);

    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_IsOwner() {
    var perms = new Permissions() {
      {
        setOwner(new User("principal"));
      }
    };
    when(pathSeg.getPath()).thenReturn(Constants.PERMISSIONS);
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);

    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_DifferentOwner() {
    var perms = new Permissions() {
      {
        setOwner(new User("different"));
      }
    };
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);

    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_Manager() {
    var perms = new Permissions() {
      {
        setManager(List.of(new User("principal")));
      }
    };
    when(pathSeg.getPath()).thenReturn(Constants.PERMISSIONS);
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);

    var actual = permissionsService.isAllowed(request, AccessType.Manage, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_NoManager() {
    var perms = new Permissions();
    when(pathSeg.getPath()).thenReturn(Constants.PERMISSIONS);
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);

    var actual = permissionsService.isAllowed(request, AccessType.Manage, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_Reader() {
    var perms = new Permissions() {
      {
        setReader(List.of(new User("principal")));
      }
    };
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);

    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_NoReader() {
    var perms = new Permissions();
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);

    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_Writer() {
    var perms = new Permissions() {
      {
        setWriter(List.of(new User("principal")));
      }
    };
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);

    var actual = permissionsService.isAllowed(request, AccessType.Write, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_WriterGroup() {
    UserGroup writerGroup = new UserGroup();
    writerGroup.setId(35L);
    User writer = new User("principal");
    ArrayList<User> users = new ArrayList<>();
    users.add(writer);
    writerGroup.setUsers(users);
    ArrayList<UserGroup> writerGroups = new ArrayList<>();
    writerGroups.add(writerGroup);
    var perms = new Permissions() {
      {
        setWriterGroups(writerGroups);
      }
    };
    when(userGroupService.getUserGroup(35L)).thenReturn(writerGroup);
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);
    assertTrue(permissionsService.isAccessTypeAllowedForUser(123, AccessType.Write, "principal"));
  }

  @Test
  public void isNotAllowedTest_WriterGroup() {
    UserGroup writerGroup = new UserGroup();
    writerGroup.setId(35L);
    User writer = new User("principal");
    ArrayList<User> users = new ArrayList<>();
    users.add(writer);
    writerGroup.setUsers(users);
    ArrayList<UserGroup> writerGroups = new ArrayList<>();
    writerGroups.add(writerGroup);
    var perms = new Permissions() {
      {
        setWriterGroups(writerGroups);
      }
    };
    when(userGroupService.getUserGroup(35L)).thenReturn(writerGroup);
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);
    assertFalse(permissionsService.isAccessTypeAllowedForUser(123, AccessType.Write, "Heinz"));
  }

  @Test
  public void isAllowedTest_ReaderGroup() {
    UserGroup readerGroup = new UserGroup();
    readerGroup.setId(35L);
    User reader = new User("principal");
    ArrayList<User> users = new ArrayList<>();
    users.add(reader);
    readerGroup.setUsers(users);
    ArrayList<UserGroup> readerGroups = new ArrayList<>();
    readerGroups.add(readerGroup);
    var perms = new Permissions() {
      {
        setReaderGroups(readerGroups);
      }
    };
    when(userGroupService.getUserGroup(35L)).thenReturn(readerGroup);
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);
    assertTrue(permissionsService.isAccessTypeAllowedForUser(123, AccessType.Read, "principal"));
  }

  @Test
  public void isNotAllowedTest_ReaderGroup() {
    UserGroup readerGroup = new UserGroup();
    readerGroup.setId(35L);
    User reader = new User("principal");
    ArrayList<User> users = new ArrayList<>();
    users.add(reader);
    readerGroup.setUsers(users);
    ArrayList<UserGroup> readerGroups = new ArrayList<>();
    readerGroups.add(readerGroup);
    var perms = new Permissions() {
      {
        setReaderGroups(readerGroups);
      }
    };
    when(userGroupService.getUserGroup(35L)).thenReturn(readerGroup);
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);
    assertFalse(permissionsService.isAccessTypeAllowedForUser(123, AccessType.Read, "AKP"));
  }

  @Test
  public void isAllowedTest_NoWriter() {
    var perms = new Permissions();
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);

    var actual = permissionsService.isAllowed(request, AccessType.Write, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_TypePrivate() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.Private);
      }
    };
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);

    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_TypeReadable() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.PublicReadable);
      }
    };
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);

    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_TypeReadableWrite() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.PublicReadable);
      }
    };
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);

    var actual = permissionsService.isAllowed(request, AccessType.Write, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_TypePublic() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.Public);
      }
    };
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);

    var actual = permissionsService.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_TypePublicWrite() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.Public);
      }
    };
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);

    var actual = permissionsService.isAllowed(request, AccessType.Write, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_InvalidAccessType() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.Public);
      }
    };
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);

    var actual = permissionsService.isAllowed(request, AccessType.None, "principal");
    assertFalse(actual);
  }

  @Test
  public void getRolesTest() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.Public);
      }
    };
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(perms);

    var expected = new Roles(false, false, true, true);
    var actual = permissionsService.getUserRolesOnEntity(123, "bob");
    assertEquals(expected, actual);
  }

  @Test
  public void getRolesTest_null() {
    when(permissionsDAO.findByEntityNeo4jId(123)).thenReturn(null);

    var expected = new Roles(false, true, true, true);
    var actual = permissionsService.getUserRolesOnEntity(123, "bob");
    assertEquals(expected, actual);
  }
}
