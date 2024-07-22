package de.dlr.shepard.filters;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.security.GracePeriodUtil;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.AccessType;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.net.URISyntaxException;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Spy;

public class PermissionsFilterTest extends BaseTestCase {

  @Mock
  private PathSegment rootSeg;

  @Mock
  private PathSegment idSeg;

  @Mock
  private PathSegment pathSeg;

  @Mock
  private UriInfo uriInfo;

  @Mock
  private UserPrincipal userPrincipal;

  @Mock
  private SecurityContext securityContext;

  @Mock
  private ContainerRequestContext context;

  @Mock
  private PermissionsUtil permissionsUtil;

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
    when(filter.getPermissionsUtil()).thenReturn(permissionsUtil);
    FieldUtils.writeField(filter, "lastSeen", lastSeen, true);
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
