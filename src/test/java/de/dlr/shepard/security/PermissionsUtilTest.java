package de.dlr.shepard.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.neo4Core.io.RolesIO;
import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.neo4Core.services.UserGroupService;
import de.dlr.shepard.util.AccessType;
import jakarta.ws.rs.core.PathSegment;

public class PermissionsUtilTest extends BaseTestCase {
	@Mock
	private PathSegment rootSeg;

	@Mock
	private PathSegment idSeg;

	@Mock
	private PathSegment pathSeg;

	@Mock
	private PathSegment thirdSegment;

	private List<PathSegment> pathSegments;

	@Mock
	private PermissionsService permissionsService;

	@Mock
	private UserGroupService userGroupService;

	@InjectMocks
	private PermissionsUtil util;

	@BeforeEach
	public void setUpRequestContext() throws URISyntaxException {
		when(rootSeg.getPath()).thenReturn("collections");
		when(idSeg.getPath()).thenReturn("123");
		when(pathSeg.getPath()).thenReturn("dataObjects");
		pathSegments = List.of(rootSeg, idSeg, pathSeg);
	}

	@Test
	public void isAllowedTest_NoId() {
		var actual = util.isAllowed(List.of(rootSeg), AccessType.Read, "principal");
		assertTrue(actual);
	}

	@Test
	public void isAllowedTest_EmptyId() {
		when(idSeg.getPath()).thenReturn("");

		var actual = util.isAllowed(pathSegments, AccessType.Read, "principal");
		assertTrue(actual);
	}

	@Test
	public void isAllowedTest_NonNumericId() {
		when(idSeg.getPath()).thenReturn("abc");

		var actual = util.isAllowed(pathSegments, AccessType.Read, "principal");
		assertFalse(actual);
	}

	@Test
	public void isAllowedTest_GetUsers() {
		when(rootSeg.getPath()).thenReturn("users");
		when(idSeg.getPath()).thenReturn("abc");

		var actual = util.isAllowed(List.of(rootSeg, idSeg), AccessType.Read, "principal");
		assertTrue(actual);
	}

	@Test
	public void isAllowedTest_EditUsers() {
		when(rootSeg.getPath()).thenReturn("users");
		when(idSeg.getPath()).thenReturn("abc");

		var actual = util.isAllowed(List.of(rootSeg, idSeg), AccessType.Write, "principal");
		assertFalse(actual);
	}

	@Test
	public void isAllowedTest_SearchUsers() {
		when(rootSeg.getPath()).thenReturn("search");
		when(idSeg.getPath()).thenReturn("users");

		var actual = util.isAllowed(List.of(rootSeg, idSeg), AccessType.Read, "principal");
		assertTrue(actual);
	}

	@Test
	public void isNotAllowedTest_SearchUsersWrongPath() {
		when(rootSeg.getPath()).thenReturn("search");
		when(idSeg.getPath()).thenReturn("user");

		var actual = util.isAllowed(List.of(rootSeg, idSeg), AccessType.Read, "principal");
		assertFalse(actual);
	}

	@Test
	public void isNotAllowedTest_SearchUsersThreeSegments() {
		when(rootSeg.getPath()).thenReturn("search");
		when(idSeg.getPath()).thenReturn("users");
		when(thirdSegment.getPath()).thenReturn("bla");

		var actual = util.isAllowed(List.of(rootSeg, idSeg, thirdSegment), AccessType.Read, "principal");
		assertFalse(actual);
	}

	@Test
	public void isAllowedTest_ManageYourself() {
		when(rootSeg.getPath()).thenReturn("users");
		when(idSeg.getPath()).thenReturn("principal");

		var actual = util.isAllowed(pathSegments, AccessType.Read, "principal");
		assertTrue(actual);
	}

	@Test
	public void isAllowedTest_ManageOther() {
		when(rootSeg.getPath()).thenReturn("users");
		when(idSeg.getPath()).thenReturn("different");

		var actual = util.isAllowed(pathSegments, AccessType.Read, "principal");
		assertFalse(actual);
	}

	@Test
	public void isAllowedTest_NoUsername() {
		var perms = new Permissions() {
			{
				setOwner(new User("principal"));
			}
		};
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		var actual = util.isAllowed(pathSegments, AccessType.Read, "");
		assertFalse(actual);
	}

	@Test
	public void isAllowedTest_NoPermissions() {
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(null);

		var actual = util.isAllowed(pathSegments, AccessType.Read, "principal");
		assertTrue(actual);
	}

	@Test
	public void isAllowedTest_IsOwner() {
		var perms = new Permissions() {
			{
				setOwner(new User("principal"));
			}
		};
		when(pathSeg.getPath()).thenReturn("permissions");
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		var actual = util.isAllowed(pathSegments, AccessType.Read, "principal");
		assertTrue(actual);
	}

	@Test
	public void isAllowedTest_DifferentOwner() {
		var perms = new Permissions() {
			{
				setOwner(new User("different"));
			}
		};
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		var actual = util.isAllowed(pathSegments, AccessType.Read, "principal");
		assertFalse(actual);
	}

	@Test
	public void isAllowedTest_Manager() {
		var perms = new Permissions() {
			{
				setManager(List.of(new User("principal")));
			}
		};
		when(pathSeg.getPath()).thenReturn("permissions");
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		var actual = util.isAllowed(pathSegments, AccessType.Manage, "principal");
		assertTrue(actual);
	}

	@Test
	public void isAllowedTest_NoManager() {
		var perms = new Permissions();
		when(pathSeg.getPath()).thenReturn("permissions");
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		var actual = util.isAllowed(pathSegments, AccessType.Manage, "principal");
		assertFalse(actual);
	}

	@Test
	public void isAllowedTest_Reader() {
		var perms = new Permissions() {
			{
				setReader(List.of(new User("principal")));
			}
		};
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		var actual = util.isAllowed(pathSegments, AccessType.Read, "principal");
		assertTrue(actual);
	}

	@Test
	public void isAllowedTest_NoReader() {
		var perms = new Permissions();
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		var actual = util.isAllowed(pathSegments, AccessType.Read, "principal");
		assertFalse(actual);
	}

	@Test
	public void isAllowedTest_Writer() {
		var perms = new Permissions() {
			{
				setWriter(List.of(new User("principal")));
			}
		};
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		var actual = util.isAllowed(pathSegments, AccessType.Write, "principal");
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
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);
		assertTrue(util.isAllowed(123, AccessType.Write, "principal"));
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
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);
		assertFalse(util.isAllowed(123, AccessType.Write, "Heinz"));
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
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);
		assertTrue(util.isAllowed(123, AccessType.Read, "principal"));
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
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);
		assertFalse(util.isAllowed(123, AccessType.Read, "AKP"));
	}

	@Test
	public void isAllowedTest_NoWriter() {
		var perms = new Permissions();
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		var actual = util.isAllowed(pathSegments, AccessType.Write, "principal");
		assertFalse(actual);
	}

	@Test
	public void isAllowedTest_TypePrivate() {
		var perms = new Permissions() {
			{
				setPermissionType(PermissionType.Private);
			}
		};
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		var actual = util.isAllowed(pathSegments, AccessType.Read, "principal");
		assertFalse(actual);
	}

	@Test
	public void isAllowedTest_TypeReadable() {
		var perms = new Permissions() {
			{
				setPermissionType(PermissionType.PublicReadable);
			}
		};
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		var actual = util.isAllowed(pathSegments, AccessType.Read, "principal");
		assertTrue(actual);
	}

	@Test
	public void isAllowedTest_TypeReadableWrite() {
		var perms = new Permissions() {
			{
				setPermissionType(PermissionType.PublicReadable);
			}
		};
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		var actual = util.isAllowed(pathSegments, AccessType.Write, "principal");
		assertFalse(actual);
	}

	@Test
	public void isAllowedTest_TypePublic() {
		var perms = new Permissions() {
			{
				setPermissionType(PermissionType.Public);
			}
		};
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		var actual = util.isAllowed(pathSegments, AccessType.Read, "principal");
		assertTrue(actual);
	}

	@Test
	public void isAllowedTest_TypePublicWrite() {
		var perms = new Permissions() {
			{
				setPermissionType(PermissionType.Public);
			}
		};
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		var actual = util.isAllowed(pathSegments, AccessType.Write, "principal");
		assertTrue(actual);
	}

	@Test
	public void isAllowedTest_InvalidAccessType() {
		var perms = new Permissions() {
			{
				setPermissionType(PermissionType.Public);
			}
		};
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		var actual = util.isAllowed(pathSegments, AccessType.None, "principal");
		assertFalse(actual);
	}

	@Test
	public void getRolesTest() {
		var perms = new Permissions() {
			{
				setPermissionType(PermissionType.Public);
			}
		};
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		var expected = new RolesIO(false, false, true, true);
		var actual = util.getRoles(123, "bob");
		assertEquals(expected, actual);
	}

	@Test
	public void getRolesTest_null() {
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(null);

		var expected = new RolesIO(false, true, true, true);
		var actual = util.getRoles(123, "bob");
		assertEquals(expected, actual);
	}

}
