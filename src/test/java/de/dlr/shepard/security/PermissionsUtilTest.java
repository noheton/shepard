package de.dlr.shepard.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.net.URISyntaxException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.PermissionType;
import jakarta.ws.rs.core.PathSegment;

public class PermissionsUtilTest extends BaseTestCase {
	@Mock
	private PathSegment rootSeg;

	@Mock
	private PathSegment idSeg;

	@Mock
	private PathSegment pathSeg;

	private List<PathSegment> pathSegments;

	@Mock
	private PermissionsService permissionsService;

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
}
