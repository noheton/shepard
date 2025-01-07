package de.dlr.shepard.common.filters;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.PermissionGracePeriod;
import de.dlr.shepard.auth.security.PermissionsUtil;
import de.dlr.shepard.common.util.AccessType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
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
  ContainerRequestContext request;

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

    URI uri = new URI("http://my.url/test/200/sub");
    when(uriInfo.getAbsolutePath()).thenReturn(uri);
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg, idSeg, pathSeg));
    when(uriInfo.getPath()).thenReturn("/shepard/api/projects");

    when(request.getSecurityContext()).thenReturn(securityContext);
    when(request.getUriInfo()).thenReturn(uriInfo);
    when(request.getMethod()).thenReturn("GET");

    when(userPrincipal.getName()).thenReturn("principal");
    when(securityContext.getUserPrincipal()).thenReturn(userPrincipal);
  }

  @Test
  public void filterTest_Read() {
    when(permissionsUtil.isAllowed(request, AccessType.Read, "principal")).thenReturn(true);

    filter.filter(request);
    verify(request, never()).abortWith(any());
  }

  @Test
  public void filterTest_Write() {
    when(request.getMethod()).thenReturn("PUT");
    when(permissionsUtil.isAllowed(request, AccessType.Write, "principal")).thenReturn(true);

    filter.filter(request);
    verify(request, never()).abortWith(any());
  }

  @Test
  public void filterTest_Manage() {
    when(pathSeg.getPath()).thenReturn("permissions");
    when(permissionsUtil.isAllowed(request, AccessType.Manage, "principal")).thenReturn(true);

    filter.filter(request);
    verify(request, never()).abortWith(any());
  }

  @Test
  public void filterTest_Invalid() {
    when(request.getMethod()).thenReturn("OPTIONS");
    when(permissionsUtil.isAllowed(request, AccessType.None, "principal")).thenReturn(false);

    filter.filter(request);
    verify(request).abortWith(any());
  }

  @Test
  public void filterTest_NotAllowed() {
    when(permissionsUtil.isAllowed(request, AccessType.Read, "principal")).thenReturn(false);

    filter.filter(request);
    verify(request).abortWith(any());
  }

  @Test
  public void filterTest_NoPrincipal() {
    when(securityContext.getUserPrincipal()).thenReturn(null);

    filter.filter(request);
    verify(request).abortWith(any());
  }

  @Test
  public void filterTest_NoPrincipalName() {
    when(userPrincipal.getName()).thenReturn(null);

    filter.filter(request);
    verify(request).abortWith(any());
  }

  @Test
  public void filterTest_lastSeen() {
    when(lastSeen.elementIsKnown("principalGET/shepard/api/projects")).thenReturn(true);

    filter.filter(request);
    verify(permissionsUtil, never()).isAllowed(any(), any(), any());
    verify(request, never()).abortWith(any());
  }
}
