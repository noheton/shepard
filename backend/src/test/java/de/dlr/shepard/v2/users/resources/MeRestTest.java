package de.dlr.shepard.v2.users.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.io.UserIO;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.v2.collectionwatchers.daos.CollectionWatcherDAO;
import de.dlr.shepard.v2.collectionwatchers.entities.CollectionWatcher;
import de.dlr.shepard.v2.users.io.MeIO;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class MeRestTest {

  static final String CALLER = "alice";
  static final String VALID_ORCID = "0000-0002-1825-0097";
  static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock
  UserService userService;

  @Mock
  UserDAO userDAO;

  @Mock
  CollectionWatcherDAO collectionWatcherDAO;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  MeRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new MeRest();
    resource.userService = userService;
    resource.userDAO = userDAO;
    resource.collectionWatcherDAO = collectionWatcherDAO;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(userService.getCurrentUser()).thenReturn(new User(CALLER, "Alice", "Anderson", "alice@example.org"));
    when(userDAO.createOrUpdate(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(collectionWatcherDAO.findByUsername(anyString())).thenReturn(List.of());
  }

  // ── CW1: GET /v2/users/me ────────────────────────────────────────────

  @Test
  void getMe_returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.getMe(securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void getMe_returns200WithZeroWatchCount() {
    when(collectionWatcherDAO.findByUsername(CALLER)).thenReturn(List.of());
    var r = resource.getMe(securityContext);
    assertEquals(200, r.getStatus());
    var body = (MeIO) r.getEntity();
    assertEquals(CALLER, body.username());
    assertEquals(0, body.watchedCollectionCount());
  }

  @Test
  void getMe_returnsCorrectWatchCount() {
    var w1 = new CollectionWatcher();
    var w2 = new CollectionWatcher();
    when(collectionWatcherDAO.findByUsername(CALLER)).thenReturn(List.of(w1, w2));
    var r = resource.getMe(securityContext);
    assertEquals(200, r.getStatus());
    var body = (MeIO) r.getEntity();
    assertEquals(2, body.watchedCollectionCount());
  }

  @Test
  void getMe_populatesUserFields() {
    var user = new User(CALLER, "Alice", "Anderson", "alice@example.org");
    user.setOrcid(VALID_ORCID);
    when(userService.getCurrentUser()).thenReturn(user);
    var r = resource.getMe(securityContext);
    assertEquals(200, r.getStatus());
    var body = (MeIO) r.getEntity();
    assertEquals("Alice", body.firstName());
    assertEquals(VALID_ORCID, body.orcid());
  }

  @Test
  void returns401WhenUnauthenticated() throws Exception {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.patchMe(MAPPER.readTree("{\"orcid\":\"" + VALID_ORCID + "\"}"), securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void returns400ForNonObjectBody() throws Exception {
    var r = resource.patchMe(MAPPER.readTree("\"not-an-object\""), securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void returns400ForNullBody() {
    var r = resource.patchMe(null, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void setsOrcidWhenValid() throws Exception {
    var r = resource.patchMe(MAPPER.readTree("{\"orcid\":\"" + VALID_ORCID + "\"}"), securityContext);
    assertEquals(200, r.getStatus());

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    org.mockito.Mockito.verify(userDAO).createOrUpdate(captor.capture());
    assertEquals(VALID_ORCID, captor.getValue().getOrcid());

    var io = (UserIO) r.getEntity();
    assertEquals(VALID_ORCID, io.getOrcid());
  }

  @Test
  void returns400WhenOrcidFailsChecksum() throws Exception {
    var r = resource.patchMe(MAPPER.readTree("{\"orcid\":\"0000-0002-1825-0098\"}"), securityContext);
    assertEquals(400, r.getStatus());
    org.mockito.Mockito.verifyNoInteractions(userDAO);
  }

  @Test
  void returns400WhenOrcidIsNotString() throws Exception {
    var r = resource.patchMe(MAPPER.readTree("{\"orcid\":42}"), securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void explicitNullClearsOrcid() throws Exception {
    var existing = new User(CALLER, "Alice", "Anderson", "alice@example.org");
    existing.setOrcid(VALID_ORCID);
    when(userService.getCurrentUser()).thenReturn(existing);

    var r = resource.patchMe(MAPPER.readTree("{\"orcid\":null}"), securityContext);
    assertEquals(200, r.getStatus());

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    org.mockito.Mockito.verify(userDAO).createOrUpdate(captor.capture());
    assertNull(captor.getValue().getOrcid());
  }

  @Test
  void emptyStringClearsOrcid() throws Exception {
    var existing = new User(CALLER, "Alice", "Anderson", "alice@example.org");
    existing.setOrcid(VALID_ORCID);
    when(userService.getCurrentUser()).thenReturn(existing);

    var r = resource.patchMe(MAPPER.readTree("{\"orcid\":\"\"}"), securityContext);
    assertEquals(200, r.getStatus());

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    org.mockito.Mockito.verify(userDAO).createOrUpdate(captor.capture());
    assertNull(captor.getValue().getOrcid());
  }

  @Test
  void absentOrcidPreservesExistingValue() throws Exception {
    // RFC 7396: missing fields are NOT changed. {} should leave orcid as-is.
    var existing = new User(CALLER, "Alice", "Anderson", "alice@example.org");
    existing.setOrcid(VALID_ORCID);
    when(userService.getCurrentUser()).thenReturn(existing);

    var r = resource.patchMe(MAPPER.readTree("{}"), securityContext);
    assertEquals(200, r.getStatus());

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    org.mockito.Mockito.verify(userDAO).createOrUpdate(captor.capture());
    assertEquals(VALID_ORCID, captor.getValue().getOrcid());
  }

  @Test
  void unknownBodyKeysAreIgnored() throws Exception {
    // RFC 7396 open-world: unknown body fields are silently ignored,
    // not rejected. v1 only patches `orcid` so anything else is a no-op.
    var r = resource.patchMe(MAPPER.readTree("{\"future-field\":\"value\"}"), securityContext);
    assertEquals(200, r.getStatus());

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    org.mockito.Mockito.verify(userDAO).createOrUpdate(captor.capture());
    assertNotNull(captor.getValue());
  }

  // ── U1b: displayName ────────────────────────────────────────────────

  @Test
  void setsDisplayNameWhenSupplied() throws Exception {
    var r = resource.patchMe(MAPPER.readTree("{\"displayName\":\"Dr. A\"}"), securityContext);
    assertEquals(200, r.getStatus());

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    org.mockito.Mockito.verify(userDAO).createOrUpdate(captor.capture());
    assertEquals("Dr. A", captor.getValue().getDisplayName());
  }

  @Test
  void displayNameExplicitNullClears() throws Exception {
    var existing = new User(CALLER, "Alice", "Anderson", "alice@example.org");
    existing.setDisplayName("Dr. A");
    when(userService.getCurrentUser()).thenReturn(existing);

    var r = resource.patchMe(MAPPER.readTree("{\"displayName\":null}"), securityContext);
    assertEquals(200, r.getStatus());

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    org.mockito.Mockito.verify(userDAO).createOrUpdate(captor.capture());
    assertNull(captor.getValue().getDisplayName());
  }

  @Test
  void displayNameEmptyStringClears() throws Exception {
    var existing = new User(CALLER, "Alice", "Anderson", "alice@example.org");
    existing.setDisplayName("Dr. A");
    when(userService.getCurrentUser()).thenReturn(existing);

    var r = resource.patchMe(MAPPER.readTree("{\"displayName\":\"\"}"), securityContext);
    assertEquals(200, r.getStatus());

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    org.mockito.Mockito.verify(userDAO).createOrUpdate(captor.capture());
    assertNull(captor.getValue().getDisplayName());
  }

  @Test
  void displayNameNonStringReturns400() throws Exception {
    var r = resource.patchMe(MAPPER.readTree("{\"displayName\":42}"), securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void orcidAndDisplayNameSetInSamePatch() throws Exception {
    // RFC 7396: a single body can update multiple fields atomically
    // from the caller's POV. The endpoint applies both before saving.
    var r = resource.patchMe(
      MAPPER.readTree("{\"orcid\":\"" + VALID_ORCID + "\",\"displayName\":\"Dr. A\"}"),
      securityContext
    );
    assertEquals(200, r.getStatus());

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    org.mockito.Mockito.verify(userDAO).createOrUpdate(captor.capture());
    var passed = captor.getValue();
    assertEquals(VALID_ORCID, passed.getOrcid());
    assertEquals("Dr. A", passed.getDisplayName());
  }
}
