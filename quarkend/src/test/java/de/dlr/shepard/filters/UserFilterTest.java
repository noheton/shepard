package de.dlr.shepard.filters;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.ShepardProcessingException;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.services.UserService;
import de.dlr.shepard.security.GracePeriodUtil;
import de.dlr.shepard.security.JWTPrincipal;
import de.dlr.shepard.security.Userinfo;
import de.dlr.shepard.security.UserinfoService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.security.Principal;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;

public class UserFilterTest extends BaseTestCase {

  @Mock
  private ContainerRequestContext requestContext;

  @Mock
  private SecurityContext securityContext;

  @Mock
  private UserService userService;

  @Mock
  private UserinfoService userinfoService;

  @Mock
  private GracePeriodUtil lastSeen;

  @Spy
  private UserFilter filter;

  @Captor
  private ArgumentCaptor<JWTPrincipal> userCaptor;

  @BeforeEach
  public void prepareSpy() throws IllegalAccessException {
    when(filter.getUserService()).thenReturn(userService);
    when(filter.getUserinfoService()).thenReturn(userinfoService);
    FieldUtils.writeField(filter, "lastSeen", lastSeen, true);
    when(requestContext.getSecurityContext()).thenReturn(securityContext);
  }

  @Test
  public void testFilter_Successful() throws IOException, ShepardProcessingException {
    Principal p = new JWTPrincipal("bob", "MyKeyId");
    Userinfo ui = new Userinfo("bob", "name", "john.doe@example.com", "John", "Doe", "doe_jo");
    User u = new User("bob", "John", "Doe", "john.doe@example.com");

    when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer abc");
    when(securityContext.getUserPrincipal()).thenReturn(p);
    when(userinfoService.fetchUserinfo("Bearer abc")).thenReturn(ui);
    when(userService.updateUser(u)).thenReturn(u);

    filter.filter(requestContext);
    verify(userService).updateUser(u);
    verify(lastSeen).elementSeen("bob");
    verify(requestContext, never()).abortWith(any());
  }

  @Test
  public void testFilter_SuccessfulUsernameConversion() throws IOException, ShepardProcessingException {
    Principal p = new JWTPrincipal("bob", "MyKeyId");
    Userinfo ui = new Userinfo("f:123:bob", "name", "john.doe@example.com", "John", "Doe", "doe_jo");
    User u = new User("bob", "John", "Doe", "john.doe@example.com");

    when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer abc");
    when(securityContext.getUserPrincipal()).thenReturn(p);
    when(userinfoService.fetchUserinfo("Bearer abc")).thenReturn(ui);
    when(userService.updateUser(u)).thenReturn(u);

    filter.filter(requestContext);
    verify(userService).updateUser(u);
    verify(lastSeen).elementSeen("bob");
    verify(requestContext, never()).abortWith(any());
  }

  @Test
  public void testFilter_GracePeriod() throws IOException {
    Principal p = new JWTPrincipal("bob", "MyKeyId");

    when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer abc");
    when(securityContext.getUserPrincipal()).thenReturn(p);
    when(lastSeen.elementIsKnown("bob")).thenReturn(true);

    filter.filter(requestContext);
    verify(userService, never()).updateUser(any());
    verify(lastSeen, never()).elementSeen(any());
  }

  @Test
  public void testFilter_NoPrincipal() throws IOException {
    when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer abc");
    when(securityContext.getUserPrincipal()).thenReturn(null);

    filter.filter(requestContext);
    verify(userService, never()).updateUser(any());
    verify(lastSeen, never()).elementSeen(any());
    verify(requestContext).abortWith(any());
  }

  @Test
  public void testFilter_InvalidPrincipal() throws IOException {
    Principal p = new Principal() {
      @Override
      public String getName() {
        return "myName";
      }
    };

    when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer abc");
    when(securityContext.getUserPrincipal()).thenReturn(p);

    filter.filter(requestContext);
    verify(userService, never()).updateUser(any());
    verify(lastSeen, never()).elementSeen(any());
    verify(requestContext).abortWith(any());
  }

  @Test
  public void testFilter_noHeader() throws IOException {
    Principal p = new JWTPrincipal("bob", "MyKeyId");

    when(securityContext.getUserPrincipal()).thenReturn(p);
    when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);

    filter.filter(requestContext);
    verify(userService, never()).updateUser(any());
    verify(lastSeen, never()).elementSeen(any());
  }

  @Test
  public void testFilter_invalidHeader() throws IOException {
    Principal p = new JWTPrincipal("bob", "MyKeyId");

    when(securityContext.getUserPrincipal()).thenReturn(p);
    when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("invalid");

    filter.filter(requestContext);
    verify(userService, never()).updateUser(any());
    verify(lastSeen, never()).elementSeen(any());
  }

  @Test
  public void testFilter_ProcessingException() throws IOException, ShepardProcessingException {
    Principal p = new JWTPrincipal("bob", "MyKeyId");

    when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer abc");
    when(securityContext.getUserPrincipal()).thenReturn(p);
    when(userinfoService.fetchUserinfo("Bearer abc")).thenThrow(new ShepardProcessingException("Message"));

    filter.filter(requestContext);
    verify(userService, never()).updateUser(any());
    verify(lastSeen, never()).elementSeen(any());
    verify(requestContext).abortWith(any());
  }

  @Test
  public void testFilter_InconsistentUsernames() throws IOException, ShepardProcessingException {
    Principal p = new JWTPrincipal("bob", "MyKeyId");
    Userinfo ui = new Userinfo("claus", "name", "john.doe@example.com", "John", "Doe", "doe_jo");

    when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer abc");
    when(securityContext.getUserPrincipal()).thenReturn(p);
    when(userinfoService.fetchUserinfo("Bearer abc")).thenReturn(ui);

    filter.filter(requestContext);
    verify(userService, never()).updateUser(any());
    verify(lastSeen, never()).elementSeen(any());
    verify(requestContext).abortWith(any());
  }

  @Test
  public void testFilter_UpdatedFailed() throws IOException, ShepardProcessingException {
    Principal p = new JWTPrincipal("bob", "MyKeyId");
    Userinfo ui = new Userinfo("bob", "name", "john.doe@example.com", "John", "Doe", "doe_jo");
    User u = new User("bob", "John", "Doe", "john.doe@example.com");

    when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer abc");
    when(securityContext.getUserPrincipal()).thenReturn(p);
    when(userinfoService.fetchUserinfo("Bearer abc")).thenReturn(ui);
    when(userService.updateUser(u)).thenReturn(null);

    filter.filter(requestContext);
    verify(lastSeen, never()).elementSeen(any());
    verify(requestContext).abortWith(any());
  }
}
