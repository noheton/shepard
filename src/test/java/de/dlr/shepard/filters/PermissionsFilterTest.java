package de.dlr.shepard.filters;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URISyntaxException;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.security.GracePeriodUtil;
import de.dlr.shepard.util.PermissionType;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

public class PermissionsFilterTest extends BaseTestCase {

	@Mock
	private PathSegment rootSeg;

	@Mock
	private PathSegment idSeg;

	@Mock
	private PathSegment pathSeg;

	@Mock
	private UserPrincipal userPrincipal;

	@Mock
	private UriInfo uriInfo;

	@Mock
	private SecurityContext securityContext;

	@Mock
	private ContainerRequestContext context;

	@Mock
	private PermissionsService permissionsService;

	@Mock
	private GracePeriodUtil lastSeen;

	@Spy
	private PermissionsFilter filter;

	@BeforeEach
	public void setUpRequestContext() throws URISyntaxException {
		when(rootSeg.getPath()).thenReturn("collections");
		when(idSeg.getPath()).thenReturn("123");
		when(pathSeg.getPath()).thenReturn("dataObjects");
		when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg, idSeg, pathSeg));

		when(uriInfo.getPath()).thenReturn("/shepard/api/projects");
		when(context.getUriInfo()).thenReturn(uriInfo);

		when(userPrincipal.getName()).thenReturn("principal");
		when(securityContext.getUserPrincipal()).thenReturn(userPrincipal);
		when(context.getSecurityContext()).thenReturn(securityContext);

		when(context.getMethod()).thenReturn("GET");
	}

	@BeforeEach
	public void prepareSpy() throws IllegalAccessException {
		when(filter.getPermissionsService()).thenReturn(permissionsService);
		FieldUtils.writeField(filter, "lastSeen", lastSeen, true);
	}

	@Test
	public void filterTest_NoId() {
		when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg));

		filter.filter(context);
		verify(context, never()).abortWith(any());
	}

	@Test
	public void filterTest_EmptyId() {
		when(idSeg.getPath()).thenReturn("");

		filter.filter(context);
		verify(context, never()).abortWith(any());
	}

	@Test
	public void filterTest_NonNumericId() {
		when(idSeg.getPath()).thenReturn("abc");

		filter.filter(context);
		verify(context).abortWith(any());
	}

	@Test
	public void filterTest_GetUsers() {
		when(rootSeg.getPath()).thenReturn("users");
		when(idSeg.getPath()).thenReturn("abc");
		when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg, idSeg));

		filter.filter(context);
		verify(context, never()).abortWith(any());
	}

	@Test
	public void filterTest_EditUsers() {
		when(rootSeg.getPath()).thenReturn("users");
		when(idSeg.getPath()).thenReturn("abc");
		when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg, idSeg));
		when(context.getMethod()).thenReturn("POST");

		filter.filter(context);
		verify(context).abortWith(any());
	}

	@Test
	public void filterTest_ManageYourself() {
		when(rootSeg.getPath()).thenReturn("users");
		when(idSeg.getPath()).thenReturn("principal");

		filter.filter(context);
		verify(context, never()).abortWith(any());
	}

	@Test
	public void filterTest_ManageOther() {
		when(rootSeg.getPath()).thenReturn("users");
		when(idSeg.getPath()).thenReturn("different");

		filter.filter(context);
		verify(context).abortWith(any());
	}

	@Test
	public void filterTest_NoPrincipal() {
		when(securityContext.getUserPrincipal()).thenReturn(null);

		filter.filter(context);
		verify(context).abortWith(any());
	}

	@Test
	public void filterTest_NoPrincipalName() {
		when(userPrincipal.getName()).thenReturn(null);

		filter.filter(context);
		verify(context).abortWith(any());
	}

	@Test
	public void filterTest_lastSeen() {
		when(lastSeen.elementIsKnown("principalGET/shepard/api/projects")).thenReturn(true);

		filter.filter(context);
		verify(permissionsService, never()).getPermissionsByEntity(123L);
		verify(context, never()).abortWith(any());
	}

	@Test
	public void filterTest_NoPermissions() {
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(null);

		filter.filter(context);
		verify(context, never()).abortWith(any());
	}

	@Test
	public void filterTest_IsOwner() {
		var perms = new Permissions() {
			{
				setOwner(new User("principal"));
			}
		};
		when(pathSeg.getPath()).thenReturn("permissions");
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		filter.filter(context);
		verify(context, never()).abortWith(any());
	}

	@Test
	public void filterTest_DifferentOwner() {
		var perms = new Permissions() {
			{
				setOwner(new User("different"));
				setPermissionType(PermissionType.Private);
			}
		};
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		filter.filter(context);
		verify(context).abortWith(any());
	}

	@Test
	public void filterTest_Manager() {
		var perms = new Permissions() {
			{
				setManager(List.of(new User("principal")));
				setPermissionType(PermissionType.Private);
			}
		};
		when(pathSeg.getPath()).thenReturn("permissions");
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		filter.filter(context);
		verify(context, never()).abortWith(any());
	}

	@Test
	public void filterTest_NoManager() {
		var perms = new Permissions();
		when(pathSeg.getPath()).thenReturn("permissions");
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);

		filter.filter(context);
		verify(context).abortWith(any());
	}

	@Test
	public void filterTest_Reader() {
		var perms = new Permissions() {
			{
				setReader(List.of(new User("principal")));
				setPermissionType(PermissionType.Private);
			}
		};
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);
		when(context.getMethod()).thenReturn("GET");

		filter.filter(context);
		verify(context, never()).abortWith(any());
	}

	@Test
	public void filterTest_NoReader() {
		var perms = new Permissions();
		perms.setPermissionType(PermissionType.Private);
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);
		when(context.getMethod()).thenReturn("GET");

		filter.filter(context);
		verify(context).abortWith(any());
	}

	@Test
	public void filterTest_Writer() {
		var perms = new Permissions() {
			{
				setWriter(List.of(new User("principal")));
				setPermissionType(PermissionType.Private);
			}
		};
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);
		when(context.getMethod()).thenReturn("POST");

		filter.filter(context);
		verify(context, never()).abortWith(any());
	}

	@Test
	public void filterTest_NoWriter() {
		var perms = new Permissions();
		perms.setPermissionType(PermissionType.Private);
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);
		when(context.getMethod()).thenReturn("POST");

		filter.filter(context);
		verify(context).abortWith(any());
	}

	@Test
	public void filterTest_NullPermission() {
		var perms = new Permissions();
		perms.setPermissionType(null);
		when(permissionsService.getPermissionsByEntity(123)).thenReturn(perms);
		when(context.getMethod()).thenReturn("POST");

		filter.filter(context);
		verify(context).abortWith(any());
	}

}
