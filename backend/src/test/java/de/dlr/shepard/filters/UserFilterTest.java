package de.dlr.shepard.filters;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.exceptions.ShepardProcessingException;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.services.UserService;
import de.dlr.shepard.security.JWTPrincipal;
import de.dlr.shepard.security.UserGracePeriod;
import de.dlr.shepard.security.Userinfo;
import de.dlr.shepard.security.UserinfoService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

@QuarkusComponentTest
public class UserFilterTest {

  @InjectMock
  ContainerRequestContext requestContext;

  @InjectMock
  SecurityContext securityContext;

  @InjectMock
  UserService userService;

  @InjectMock
  UserinfoService userinfoService;

  @InjectMock
  UserGracePeriod lastSeen;

  @InjectMock
  UriInfo uriInfo;

  @Inject
  UserFilter filter;

  @Captor
  ArgumentCaptor<JWTPrincipal> userCaptor;

  @BeforeEach
  public void prepareSpy() throws IllegalAccessException {
    when(requestContext.getSecurityContext()).thenReturn(securityContext);
  }

  @Test
  public void testFilterPublic_publicRoute() throws URISyntaxException, IOException {
    String relativePath = "/versionz";
    when(uriInfo.getPath()).thenReturn(relativePath);
    when(requestContext.getUriInfo()).thenReturn(uriInfo);
    filter.filter(requestContext);
    verify(requestContext, never()).abortWith(any());
  }

  @Test
  public void testFilterPublic_privateRoute() throws URISyntaxException, IOException {
    String relativePath = "/versionsz";
    when(uriInfo.getPath()).thenReturn(relativePath);
    when(requestContext.getUriInfo()).thenReturn(uriInfo);
    filter.filter(requestContext);
    verify(requestContext).abortWith(any());
  }

  @Test
  public void testFilter_Successful() throws IOException, ShepardProcessingException {
    Principal p = new JWTPrincipal("bob", "MyKeyId");
    Userinfo ui = new Userinfo("bob", "name", "john.doe@example.com", "John", "Doe", "doe_jo");
    User u = new User("bob", "John", "Doe", "john.doe@example.com");

    when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer abc");
    String relativePath = "/projects";
    when(uriInfo.getPath()).thenReturn(relativePath);
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

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
    String relativePath = "/projects";
    when(uriInfo.getPath()).thenReturn(relativePath);
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

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
    String relativePath = "/projects";
    when(uriInfo.getPath()).thenReturn(relativePath);
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

    filter.filter(requestContext);
    verify(userService, never()).updateUser(any());
    verify(lastSeen, never()).elementSeen(any());
  }

  @Test
  public void testFilter_NoPrincipal() throws IOException {
    when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer abc");
    when(securityContext.getUserPrincipal()).thenReturn(null);
    String relativePath = "/projects";
    when(uriInfo.getPath()).thenReturn(relativePath);
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

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
    String relativePath = "/projects";
    when(uriInfo.getPath()).thenReturn(relativePath);
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

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
    String relativePath = "/projects";
    when(uriInfo.getPath()).thenReturn(relativePath);
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

    filter.filter(requestContext);
    verify(userService, never()).updateUser(any());
    verify(lastSeen, never()).elementSeen(any());
  }

  @Test
  public void testFilter_invalidHeader() throws IOException {
    Principal p = new JWTPrincipal("bob", "MyKeyId");

    when(securityContext.getUserPrincipal()).thenReturn(p);
    when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("invalid");
    String relativePath = "/projects";
    when(uriInfo.getPath()).thenReturn(relativePath);
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

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
    String relativePath = "/projects";
    when(uriInfo.getPath()).thenReturn(relativePath);
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

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
    String relativePath = "/projects";
    when(uriInfo.getPath()).thenReturn(relativePath);
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

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
    String relativePath = "/projects";
    when(uriInfo.getPath()).thenReturn(relativePath);
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

    filter.filter(requestContext);
    verify(lastSeen, never()).elementSeen(any());
    verify(requestContext).abortWith(any());
  }
}
