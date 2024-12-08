package de.dlr.shepard.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.labJournal.services.LabJournalEntryService;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.neo4Core.io.RolesIO;
import de.dlr.shepard.neo4Core.services.DataObjectService;
import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.neo4Core.services.UserGroupService;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.PermissionType;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PermissionsUtilTest extends BaseTestCase {

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
  private PermissionsService permissionsService;

  @Mock
  private UserGroupService userGroupService;

  @Mock
  private DataObjectService dataObjectService;

  @Mock
  private LabJournalEntryService labJournalEntryService;

  @InjectMocks
  private PermissionsUtil util;

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
    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_EmptyId() {
    when(idSeg.getPath()).thenReturn("");

    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_NonNumericId() {
    when(idSeg.getPath()).thenReturn("abc");

    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_GetUsers() {
    when(rootSeg.getPath()).thenReturn(Constants.USERS);
    when(idSeg.getPath()).thenReturn("abc");
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg, idSeg));
    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_EditUsers() {
    when(rootSeg.getPath()).thenReturn(Constants.USERS);
    when(idSeg.getPath()).thenReturn("abc");
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg, idSeg));
    var actual = util.isAllowed(request, AccessType.Write, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_SearchUsers() {
    when(rootSeg.getPath()).thenReturn(Constants.SEARCH);
    when(idSeg.getPath()).thenReturn(Constants.USERS);
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg, idSeg));
    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isNotAllowedTest_SearchUsersWrongPath() {
    when(rootSeg.getPath()).thenReturn(Constants.SEARCH);
    when(idSeg.getPath()).thenReturn(Constants.USERS);
    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertFalse(actual);
  }

  @Test
  public void isNotAllowedTest_SearchUsersThreeSegments() {
    when(rootSeg.getPath()).thenReturn(Constants.SEARCH);
    when(idSeg.getPath()).thenReturn(Constants.USERS);
    when(thirdSegment.getPath()).thenReturn("bla");
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg, idSeg, thirdSegment));
    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_ManageYourself() {
    when(rootSeg.getPath()).thenReturn(Constants.USERS);
    when(idSeg.getPath()).thenReturn("principal");

    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_ManageOther() {
    when(rootSeg.getPath()).thenReturn(Constants.USERS);
    when(idSeg.getPath()).thenReturn("different");

    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_NoUsername() {
    var perms = new Permissions() {
      {
        setOwner(new User("principal"));
      }
    };
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);

    var actual = util.isAllowed(request, AccessType.Read, "");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_NoPermissions() {
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(null);

    var actual = util.isAllowed(request, AccessType.Read, "principal");
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
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);

    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_DifferentOwner() {
    var perms = new Permissions() {
      {
        setOwner(new User("different"));
      }
    };
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);

    var actual = util.isAllowed(request, AccessType.Read, "principal");
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
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);

    var actual = util.isAllowed(request, AccessType.Manage, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_NoManager() {
    var perms = new Permissions();
    when(pathSeg.getPath()).thenReturn(Constants.PERMISSIONS);
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);

    var actual = util.isAllowed(request, AccessType.Manage, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_Reader() {
    var perms = new Permissions() {
      {
        setReader(List.of(new User("principal")));
      }
    };
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);

    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_NoReader() {
    var perms = new Permissions();
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);

    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_Writer() {
    var perms = new Permissions() {
      {
        setWriter(List.of(new User("principal")));
      }
    };
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);

    var actual = util.isAllowed(request, AccessType.Write, "principal");
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
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);
    assertTrue(util.isAccessTypeAllowedForUser(123, AccessType.Write, "principal"));
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
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);
    assertFalse(util.isAccessTypeAllowedForUser(123, AccessType.Write, "Heinz"));
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
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);
    assertTrue(util.isAccessTypeAllowedForUser(123, AccessType.Read, "principal"));
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
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);
    assertFalse(util.isAccessTypeAllowedForUser(123, AccessType.Read, "AKP"));
  }

  @Test
  public void isAllowedTest_NoWriter() {
    var perms = new Permissions();
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);

    var actual = util.isAllowed(request, AccessType.Write, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_TypePrivate() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.Private);
      }
    };
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);

    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_TypeReadable() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.PublicReadable);
      }
    };
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);

    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_TypeReadableWrite() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.PublicReadable);
      }
    };
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);

    var actual = util.isAllowed(request, AccessType.Write, "principal");
    assertFalse(actual);
  }

  @Test
  public void isAllowedTest_TypePublic() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.Public);
      }
    };
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);

    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_TypePublicWrite() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.Public);
      }
    };
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);

    var actual = util.isAllowed(request, AccessType.Write, "principal");
    assertTrue(actual);
  }

  @Test
  public void isAllowedTest_InvalidAccessType() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.Public);
      }
    };
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);

    var actual = util.isAllowed(request, AccessType.None, "principal");
    assertFalse(actual);
  }

  @Test
  public void getRolesTest() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.Public);
      }
    };
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(perms);

    var expected = new RolesIO(false, false, true, true);
    var actual = util.getRolesByNeo4jId(123, "bob");
    assertEquals(expected, actual);
  }

  @Test
  public void getRolesTest_null() {
    when(permissionsService.getPermissionsByNeo4jId(123)).thenReturn(null);

    var expected = new RolesIO(false, true, true, true);
    var actual = util.getRolesByNeo4jId(123, "bob");
    assertEquals(expected, actual);
  }

  @Test
  @DisplayName("Tests allowed lab journal entries request with no id segment")
  public void isAllowedTest_labJournals() {
    when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>() {});
    when(rootSeg.getPath()).thenReturn(Constants.LAB_JOURNAL_ENTRIES);
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg));
    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
    actual = util.isAllowed(request, AccessType.Write, "principal");
    assertTrue(actual);
  }

  @Test
  @DisplayName("Tests allowed lab journal entries request with id segment")
  public void isAllowedTest_labJournalEntriesWithIdSegment() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.Private);
        setOwner(new User("principal"));
      }
    };
    when(permissionsService.getPermissionsByNeo4jId(123L)).thenReturn(perms);
    when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>() {});
    when(rootSeg.getPath()).thenReturn(Constants.LAB_JOURNAL_ENTRIES);
    when(idSeg.getPath()).thenReturn("100");
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg, idSeg));
    when(labJournalEntryService.getCollectionId(100L)).thenReturn(123L);
    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertTrue(actual);
    actual = util.isAllowed(request, AccessType.Write, "principal");
    assertTrue(actual);
  }

  @Test
  @DisplayName("Tests not allowed lab journal entries request with id segment")
  public void isNotAllowedTest_labJournalEntriesWithIdSegment() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.Private);
        setOwner(new User("some_user"));
      }
    };
    when(permissionsService.getPermissionsByNeo4jId(123L)).thenReturn(perms);
    when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>() {});
    when(rootSeg.getPath()).thenReturn(Constants.LAB_JOURNAL_ENTRIES);
    when(idSeg.getPath()).thenReturn("100");
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg, idSeg));
    when(labJournalEntryService.getCollectionId(100L)).thenReturn(123L);
    var actual = util.isAllowed(request, AccessType.Read, "principal");
    assertFalse(actual);
    actual = util.isAllowed(request, AccessType.Write, "principal");
    assertFalse(actual);
  }

  @Test
  @DisplayName("Tests allowed lab journal entries request with object id in body")
  public void isAllowedTest_labJournalEntriesWithObjectId() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.Private);
        setOwner(new User("principal"));
      }
    };
    when(permissionsService.getPermissionsByNeo4jId(123L)).thenReturn(perms);
    MultivaluedHashMap<String, String> params = new MultivaluedHashMap<>();
    params.add(Constants.DATA_OBJECT_ID, "100");
    when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>() {});
    when(rootSeg.getPath()).thenReturn(Constants.LAB_JOURNAL_ENTRIES);
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg));
    var actual = util.isAllowed(request, AccessType.Write, "principal");
    assertTrue(actual);
  }

  @Test
  @DisplayName("Tests not allowed lab journal request with object id in body")
  public void isNotAllowedTest_labJournalEntriesWithObjectId() {
    var perms = new Permissions() {
      {
        setPermissionType(PermissionType.Private);
        setOwner(new User("some_user"));
      }
    };
    when(permissionsService.getPermissionsByNeo4jId(123L)).thenReturn(perms);
    MultivaluedHashMap<String, String> params = new MultivaluedHashMap<>();
    params.add(Constants.DATA_OBJECT_ID, "100");
    when(uriInfo.getQueryParameters()).thenReturn(params);
    when(rootSeg.getPath()).thenReturn(Constants.LAB_JOURNAL_ENTRIES);
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg));
    when(dataObjectService.getCollectionId(100L)).thenReturn(123L);
    var actual = util.isAllowed(request, AccessType.Write, "principal");
    assertFalse(actual);
  }
}
