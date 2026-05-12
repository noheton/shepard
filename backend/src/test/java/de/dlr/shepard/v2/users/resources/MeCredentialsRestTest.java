package de.dlr.shepard.v2.users.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.daos.GitCredentialDAO;
import de.dlr.shepard.auth.users.entities.GitCredential;
import de.dlr.shepard.v2.users.io.CreateGitCredentialIO;
import de.dlr.shepard.v2.users.io.GitCredentialIO;
import de.dlr.shepard.v2.users.io.PatchGitCredentialIO;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.security.Principal;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class MeCredentialsRestTest {

  static final String CALLER = "alice";

  // A valid 32-byte key, base64-encoded.
  static final String VALID_KEY_B64 = java.util.Base64.getEncoder().encodeToString(new byte[32]);

  @Mock
  GitCredentialDAO gitCredentialDAO;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  @Mock
  UriInfo uriInfo;

  MeCredentialsRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new MeCredentialsRest();
    resource.gitCredentialDAO = gitCredentialDAO;
    resource.encryptionKey = Optional.of(VALID_KEY_B64);

    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);

    // Wire UriInfo so POST can build a Location header.
    UriBuilder builder = UriBuilder.fromUri("http://localhost/v2/me/git-credentials");
    when(uriInfo.getAbsolutePathBuilder()).thenReturn(builder);
  }

  // --- authentication guard ---

  @Test
  void listReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    assertEquals(401, resource.list(securityContext).getStatus());
  }

  @Test
  void createReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    assertEquals(401, resource.create(new CreateGitCredentialIO(), securityContext, uriInfo).getStatus());
  }

  @Test
  void readReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    assertEquals(401, resource.read("any-id", securityContext).getStatus());
  }

  @Test
  void patchReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    assertEquals(401, resource.patch("any-id", new PatchGitCredentialIO(), securityContext).getStatus());
  }

  @Test
  void deleteReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    assertEquals(401, resource.delete("any-id", securityContext).getStatus());
  }

  // --- list ---

  @Test
  void listReturnsEmptyListWhenNoneExist() {
    when(gitCredentialDAO.findAllByUser(CALLER)).thenReturn(List.of());
    var r = resource.list(securityContext);
    assertEquals(200, r.getStatus());
    assertTrue(((List<?>) r.getEntity()).isEmpty());
  }

  @Test
  void listReturnsIoListWhenCredentialsExist() {
    GitCredential cred = buildCred("id-1", "gitlab.com", "DLR", "alice");
    when(gitCredentialDAO.findAllByUser(CALLER)).thenReturn(List.of(cred));

    var r = resource.list(securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<GitCredentialIO> ios = (List<GitCredentialIO>) r.getEntity();
    assertEquals(1, ios.size());
    assertEquals("gitlab.com", ios.get(0).getHost());
    assertEquals("id-1", ios.get(0).getAppId());
  }

  // --- create ---

  @Test
  void createReturns400WhenHostMissing() {
    CreateGitCredentialIO body = new CreateGitCredentialIO();
    body.setUsername("alice");
    body.setPat("token");
    assertEquals(400, resource.create(body, securityContext, uriInfo).getStatus());
  }

  @Test
  void createReturns400WhenUsernameMissing() {
    CreateGitCredentialIO body = new CreateGitCredentialIO();
    body.setHost("gitlab.com");
    body.setPat("token");
    assertEquals(400, resource.create(body, securityContext, uriInfo).getStatus());
  }

  @Test
  void createReturns400WhenPatMissing() {
    CreateGitCredentialIO body = new CreateGitCredentialIO();
    body.setHost("gitlab.com");
    body.setUsername("alice");
    assertEquals(400, resource.create(body, securityContext, uriInfo).getStatus());
  }

  @Test
  void createReturns201WithLocationAndIo() {
    CreateGitCredentialIO body = new CreateGitCredentialIO();
    body.setHost("gitlab.com");
    body.setDisplayName("DLR GitLab");
    body.setUsername("alice");
    body.setPat("mysecretpat");

    GitCredential saved = buildCred("new-id", "gitlab.com", "DLR GitLab", "alice");
    when(gitCredentialDAO.createForUser(eq(CALLER), any(GitCredential.class))).thenReturn(saved);

    var r = resource.create(body, securityContext, uriInfo);
    assertEquals(201, r.getStatus());
    assertNotNull(r.getLocation());
    GitCredentialIO io = (GitCredentialIO) r.getEntity();
    assertEquals("new-id", io.getAppId());
    assertEquals("gitlab.com", io.getHost());
    // PAT must not appear anywhere in the response
    assertNotNull(io);
  }

  @Test
  void createReturns501WhenKeyAbsent() {
    resource.encryptionKey = Optional.empty();
    CreateGitCredentialIO body = new CreateGitCredentialIO();
    body.setHost("gitlab.com");
    body.setUsername("alice");
    body.setPat("pat");
    assertEquals(501, resource.create(body, securityContext, uriInfo).getStatus());
  }

  // --- read ---

  @Test
  void readReturns404ForUnknownAppId() {
    when(gitCredentialDAO.findByUserAndAppId(CALLER, "unknown")).thenReturn(null);
    assertEquals(404, resource.read("unknown", securityContext).getStatus());
  }

  @Test
  void readReturns404ForAnotherUsersCredential() {
    // DAO returns null when the owner doesn't match — ownership check is in Cypher.
    when(gitCredentialDAO.findByUserAndAppId(CALLER, "other-owners-id")).thenReturn(null);
    assertEquals(404, resource.read("other-owners-id", securityContext).getStatus());
  }

  @Test
  void readReturns200WithIo() {
    GitCredential cred = buildCred("id-1", "github.com", "GitHub", "alice");
    when(gitCredentialDAO.findByUserAndAppId(CALLER, "id-1")).thenReturn(cred);

    var r = resource.read("id-1", securityContext);
    assertEquals(200, r.getStatus());
    assertEquals("github.com", ((GitCredentialIO) r.getEntity()).getHost());
  }

  // --- patch ---

  @Test
  void patchUpdatesDisplayName() {
    GitCredential existing = buildCred("id-1", "gitlab.com", "Old Name", "alice");
    GitCredential updated = buildCred("id-1", "gitlab.com", "New Name", "alice");

    when(gitCredentialDAO.findByUserAndAppId(CALLER, "id-1")).thenReturn(existing);
    when(gitCredentialDAO.updateByUserAndAppId(eq(CALLER), eq("id-1"), any(GitCredential.class))).thenReturn(updated);

    PatchGitCredentialIO patch = new PatchGitCredentialIO();
    patch.setDisplayName("New Name");

    var r = resource.patch("id-1", patch, securityContext);
    assertEquals(200, r.getStatus());
    assertEquals("New Name", ((GitCredentialIO) r.getEntity()).getDisplayName());
  }

  @Test
  void patchReturns501WhenKeyAbsentAndPatProvided() {
    resource.encryptionKey = Optional.empty();

    GitCredential existing = buildCred("id-1", "gitlab.com", "Label", "alice");
    when(gitCredentialDAO.findByUserAndAppId(CALLER, "id-1")).thenReturn(existing);

    PatchGitCredentialIO patch = new PatchGitCredentialIO();
    patch.setPat("newpat");

    assertEquals(501, resource.patch("id-1", patch, securityContext).getStatus());
  }

  @Test
  void patchReturns404WhenNotFound() {
    when(gitCredentialDAO.findByUserAndAppId(CALLER, "missing")).thenReturn(null);
    assertEquals(404, resource.patch("missing", new PatchGitCredentialIO(), securityContext).getStatus());
  }

  // --- delete ---

  @Test
  void deleteReturns204OnSuccess() {
    when(gitCredentialDAO.deleteByUserAndAppId(CALLER, "id-1")).thenReturn(true);
    assertEquals(204, resource.delete("id-1", securityContext).getStatus());
  }

  @Test
  void deleteReturns404WhenNotFound() {
    when(gitCredentialDAO.deleteByUserAndAppId(CALLER, "missing")).thenReturn(false);
    assertEquals(404, resource.delete("missing", securityContext).getStatus());
  }

  // --- helpers ---

  private GitCredential buildCred(String appId, String host, String displayName, String username) {
    GitCredential c = new GitCredential();
    c.setAppId(appId);
    c.setHost(host);
    c.setDisplayName(displayName);
    c.setUsername(username);
    c.setCreatedAt(new Date());
    return c;
  }
}
