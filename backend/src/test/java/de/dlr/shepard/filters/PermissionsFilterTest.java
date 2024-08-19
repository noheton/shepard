package de.dlr.shepard.filters;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.security.PermissionGracePeriod;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.AccessType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.net.URISyntaxException;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class PermissionsFilterTest {

  @InjectMock
  PathSegment rootSeg;

  @InjectMock
  PathSegment idSeg;

  @InjectMock
  PathSegment pathSeg;

  @InjectMock
  UriInfo uriInfo;

  @InjectMock
  UserPrincipal userPrincipal;

  @InjectMock
  SecurityContext securityContext;

  @InjectMock
  ContainerRequestContext context;

  @InjectMock
  PermissionsUtil permissionsUtil;

  @InjectMock
  PermissionGracePeriod lastSeen;

  @Inject
  PermissionsFilter filter;

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

  @Test
  public void filterTest_Read() {
    when(permissionsUtil.isAllowed(uriInfo.getPathSegments(), AccessType.Read, "principal")).thenReturn(true);

    filter.filter(context);
    verify(context, never()).abortWith(any());
  }

  @Test
  public void filterTest_Write() {
    when(context.getMethod()).thenReturn("PUT");
    when(permissionsUtil.isAllowed(uriInfo.getPathSegments(), AccessType.Write, "principal")).thenReturn(true);

    filter.filter(context);
    verify(context, never()).abortWith(any());
  }

  @Test
  public void filterTest_Manage() {
    when(pathSeg.getPath()).thenReturn("permissions");
    when(permissionsUtil.isAllowed(uriInfo.getPathSegments(), AccessType.Manage, "principal")).thenReturn(true);

    filter.filter(context);
    verify(context, never()).abortWith(any());
  }

  @Test
  public void filterTest_Invalid() {
    when(context.getMethod()).thenReturn("OPTIONS");
    when(permissionsUtil.isAllowed(uriInfo.getPathSegments(), AccessType.None, "principal")).thenReturn(false);

    filter.filter(context);
    verify(context).abortWith(any());
  }

  @Test
  public void filterTest_NotAllowed() {
    when(permissionsUtil.isAllowed(uriInfo.getPathSegments(), AccessType.Read, "principal")).thenReturn(false);

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
    verify(permissionsUtil, never()).isAllowed(any(), any(), any());
    verify(context, never()).abortWith(any());
  }
}
