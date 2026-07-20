package de.dlr.shepard.auth.users.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.subscription.entities.Subscription;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@QuarkusComponentTest
public class UserServiceTest {

  @InjectMock
  UserDAO dao;

  @InjectMock
  AuthenticationContext authCtx;

  @Inject
  UserService service;

  @Test
  public void createUserTest() {
    var user = new User("Bob");
    when(dao.createOrUpdate(user)).thenReturn(user);
    var actual = service.createOrUpdateUser(user);
    assertEquals(user, actual);
  }

  @Test
  public void getUserTest() {
    var user = new User("Bob");
    when(dao.find("Bob")).thenReturn(user);
    var actual = service.getUser("Bob");
    assertEquals(user, actual);
  }

  @Test
  public void updateUserTest_noUpdate() {
    var old = new User("bob", "John", "Doe", "john.doe@example.com");
    var user = new User("bob", "John", "Doe", "john.doe@example.com");

    when(dao.find("bob")).thenReturn(old);

    var actual = service.createOrUpdateUser(user);
    verify(dao, never()).createOrUpdate(any(User.class));
    assertEquals(old, actual);
  }

  @Test
  public void updateUserTest_noUser() {
    var user = new User("bob", "John", "Doe", "john.doe@example.com");

    when(dao.find("bob")).thenReturn(null);
    when(dao.createOrUpdate(user)).thenReturn(user);

    var actual = service.createOrUpdateUser(user);
    assertEquals(user, actual);
  }

  @Test
  public void updateUserTest_updateFirstName() {
    var uid = UUID.randomUUID();
    var old = new User("bob", "John", "Doe", "john.doe@example.com");
    old.setApiKeys(List.of(new ApiKey(uid)));
    old.setSubscriptions(List.of(new Subscription(3L)));
    var user = new User("bob", "new", "Doe", "john.doe@example.com");
    var expected = new User("bob", "new", "Doe", "john.doe@example.com");
    expected.setApiKeys(List.of(new ApiKey(uid)));
    expected.setSubscriptions(List.of(new Subscription(3L)));

    when(dao.find("bob")).thenReturn(old);
    when(dao.createOrUpdate(expected)).thenReturn(expected);

    var actual = service.createOrUpdateUser(user);
    assertEquals(expected, actual);
  }

  @Test
  public void updateUserTest_updateLastName() {
    var uid = UUID.randomUUID();
    var old = new User("bob", "John", "Doe", "john.doe@example.com");
    old.setApiKeys(List.of(new ApiKey(uid)));
    old.setSubscriptions(List.of(new Subscription(3L)));
    var user = new User("bob", "John", "new", "john.doe@example.com");
    var expected = new User("bob", "John", "new", "john.doe@example.com");
    expected.setApiKeys(List.of(new ApiKey(uid)));
    expected.setSubscriptions(List.of(new Subscription(3L)));

    when(dao.find("bob")).thenReturn(old);
    when(dao.createOrUpdate(expected)).thenReturn(expected);

    var actual = service.createOrUpdateUser(user);
    assertEquals(expected, actual);
  }

  @Test
  public void updateUserTest_updateEmail() {
    var uid = UUID.randomUUID();
    var old = new User("bob", "John", "Doe", "john.doe@example.com");
    old.setApiKeys(List.of(new ApiKey(uid)));
    old.setSubscriptions(List.of(new Subscription(3L)));
    var user = new User("bob", "John", "Doe", "new@example.com");
    var expected = new User("bob", "John", "Doe", "new@example.com");
    expected.setApiKeys(List.of(new ApiKey(uid)));
    expected.setSubscriptions(List.of(new Subscription(3L)));

    when(dao.find("bob")).thenReturn(old);
    when(dao.createOrUpdate(expected)).thenReturn(expected);

    var actual = service.createOrUpdateUser(user);
    assertEquals(expected, actual);
  }

  @Test
  public void updateUserTest_emptyInput() {
    var uid = UUID.randomUUID();
    var old = new User("bob", "John", "Doe", "john.doe@example.com");
    old.setApiKeys(List.of(new ApiKey(uid)));
    old.setSubscriptions(List.of(new Subscription(3L)));
    var user = new User("bob");
    user.setFirstName(null);
    user.setLastName(null);
    user.setEmail(null);

    when(dao.find("bob")).thenReturn(old);

    var actual = service.createOrUpdateUser(user);
    assertEquals(old, actual);
  }

  // ── U1d: getPreferences ──────────────────────────────────────────────────

  @Test
  public void getPreferences_nullJson_returnsEmptyMap() {
    var user = new User("alice");
    user.setPreferencesJson(null);
    when(dao.find("alice")).thenReturn(user);

    var prefs = service.getPreferences("alice");
    assertTrue(prefs.isEmpty());
  }

  @Test
  public void getPreferences_blankJson_returnsEmptyMap() {
    var user = new User("alice");
    user.setPreferencesJson("   ");
    when(dao.find("alice")).thenReturn(user);

    var prefs = service.getPreferences("alice");
    assertTrue(prefs.isEmpty());
  }

  @Test
  public void getPreferences_validJson_returnsMap() {
    var user = new User("alice");
    user.setPreferencesJson("{\"theme\":\"dark\",\"language\":\"de\"}");
    when(dao.find("alice")).thenReturn(user);

    var prefs = service.getPreferences("alice");
    assertEquals(2, prefs.size());
    assertEquals("dark", prefs.get("theme"));
    assertEquals("de", prefs.get("language"));
  }

  @Test
  public void getPreferences_malformedJson_throwsInvalidRequestException() {
    var user = new User("alice");
    user.setPreferencesJson("{not-valid-json");
    when(dao.find("alice")).thenReturn(user);

    assertThrows(InvalidRequestException.class, () -> service.getPreferences("alice"));
  }

  // ── U1d: patchPreferences ────────────────────────────────────────────────

  @Test
  public void patchPreferences_setsNewKey_persistsJson() {
    var user = new User("alice");
    user.setPreferencesJson(null);
    when(dao.find("alice")).thenReturn(user);
    when(dao.createOrUpdate(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    var result = service.patchPreferences("alice", Map.of("theme", "dark"));
    assertEquals("dark", result.get("theme"));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(dao).createOrUpdate(captor.capture());
    assertTrue(captor.getValue().getPreferencesJson().contains("\"theme\""));
    assertTrue(captor.getValue().getPreferencesJson().contains("\"dark\""));
  }

  @Test
  public void patchPreferences_mergeRemoveAndAdd() {
    var user = new User("alice");
    user.setPreferencesJson("{\"theme\":\"dark\",\"language\":\"de\"}");
    when(dao.find("alice")).thenReturn(user);
    when(dao.createOrUpdate(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    // patch: remove "language" (null), add "timeZone"
    var patch = new java.util.HashMap<String, String>();
    patch.put("language", null);
    patch.put("timeZone", "UTC");
    var result = service.patchPreferences("alice", patch);

    assertEquals("dark", result.get("theme"));
    assertEquals("UTC", result.get("timeZone"));
    assertTrue(!result.containsKey("language"), "language key should be removed");
    assertEquals(2, result.size());
  }

  @Test
  public void patchPreferences_allKeysRemoved_setsJsonToNull() {
    var user = new User("alice");
    user.setPreferencesJson("{\"theme\":\"dark\"}");
    when(dao.find("alice")).thenReturn(user);
    when(dao.createOrUpdate(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    var patch = new java.util.HashMap<String, String>();
    patch.put("theme", null);
    service.patchPreferences("alice", patch);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(dao).createOrUpdate(captor.capture());
    assertNull(captor.getValue().getPreferencesJson(), "preferencesJson must be null when map is empty");
  }

  // ── BUG-USER-PROVISION-EMAIL-COLLISION regression tests ──────────────────

  /**
   * When find-by-username misses but find-by-email hits (stored node has a
   * different username), createOrUpdateUser must return the existing node
   * without throwing and without creating a duplicate.
   *
   * <p>Scenario: importer service created a node with username = "uuid-service"
   * and email = "admin@demo.shepard.local". Interactive admin login arrives with
   * username = "admin", same email. Old code → blind create → email unique
   * constraint violation → 500.  Fixed code → email fallback → returns existing
   * node.
   */
  @Test
  public void createOrUpdateUser_emailCollision_returnsExistingNode() {
    var existingNode = new User("uuid-service", "Admin", "User", "admin@demo.shepard.local");
    var tokenUser = new User("admin", "Admin", "User", "admin@demo.shepard.local");

    when(dao.find("admin")).thenReturn(null);
    when(dao.findByEmail("admin@demo.shepard.local")).thenReturn(Optional.of(existingNode));

    var result = service.createOrUpdateUser(tokenUser);

    assertEquals(existingNode, result, "must return the email-matched node, not create a new one");
    verify(dao, never()).createOrUpdate(tokenUser);
  }

  /**
   * With the email-collision fix, profile fields are updated on the existing
   * node (found by email) just like they are when found by username.
   */
  @Test
  public void createOrUpdateUser_emailCollision_updatesProfileOnExistingNode() {
    var existingNode = new User("uuid-service", "Old", "Name", "admin@demo.shepard.local");
    var tokenUser = new User("admin", "Admin", "User", "admin@demo.shepard.local");

    when(dao.find("admin")).thenReturn(null);
    when(dao.findByEmail("admin@demo.shepard.local")).thenReturn(Optional.of(existingNode));
    when(dao.createOrUpdate(existingNode)).thenReturn(existingNode);

    service.createOrUpdateUser(tokenUser);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(dao).createOrUpdate(captor.capture());
    assertEquals("Admin", captor.getValue().getFirstName());
    assertEquals("User", captor.getValue().getLastName());
  }

  /**
   * getCurrentUser must succeed via email fallback when the stored username
   * diverges from the token's username (cached-path scenario: no provision
   * runs, but the node carries a different username key).
   */
  @Test
  public void getCurrentUser_emailFallback_succeedsWhenUsernameMissesButEmailHits() {
    var storedNode = new User("uuid-service", "Admin", "User", "admin@demo.shepard.local");

    when(authCtx.getCurrentUserName()).thenReturn("admin");
    when(authCtx.getCurrentUserEmail()).thenReturn("admin@demo.shepard.local");
    when(dao.find("admin")).thenReturn(null);
    when(dao.findByEmail("admin@demo.shepard.local")).thenReturn(Optional.of(storedNode));

    var result = service.getCurrentUser();

    assertEquals(storedNode, result, "email fallback must return the stored node");
  }

  /**
   * NEO-AUDIT-2026-07-20-USER-SUPERNODE: getCurrentUserLight must resolve via the
   * depth-0 {@code findLight} loader (never the DEPTH_ENTITY {@code find} that
   * hydrates the :User supernode's millions of provenance edges).
   */
  @Test
  public void getCurrentUserLight_usesShallowLoader_neverHydrates() {
    var node = new User("uuid-service", "Admin", "User", "admin@demo.shepard.local");
    when(authCtx.getCurrentUserName()).thenReturn("admin");
    when(dao.findLight("admin")).thenReturn(node);

    var result = service.getCurrentUserLight();

    assertEquals(node, result);
    verify(dao).findLight("admin");
    verify(dao, never()).find("admin");
  }

  /**
   * getCurrentUserLight must share getCurrentUser's BUG-USER-PROVISION-EMAIL-COLLISION
   * email fallback (username miss → resolve by email), so identity resolution is
   * identical between the two variants.
   */
  @Test
  public void getCurrentUserLight_emailFallback_succeedsWhenUsernameMissesButEmailHits() {
    var storedNode = new User("uuid-service", "Admin", "User", "admin@demo.shepard.local");
    when(authCtx.getCurrentUserName()).thenReturn("admin");
    when(authCtx.getCurrentUserEmail()).thenReturn("admin@demo.shepard.local");
    when(dao.findLight("admin")).thenReturn(null);
    when(dao.findByEmail("admin@demo.shepard.local")).thenReturn(Optional.of(storedNode));

    var result = service.getCurrentUserLight();

    assertEquals(storedNode, result, "light variant must share the email fallback");
  }

  /**
   * getCurrentUser must still throw when both username and email lookups miss.
   */
  @Test
  public void getCurrentUser_throwsWhenNeitherUsernameNorEmailMatch() {
    when(authCtx.getCurrentUserName()).thenReturn("ghost");
    when(authCtx.getCurrentUserEmail()).thenReturn("ghost@nowhere.example");
    when(dao.find("ghost")).thenReturn(null);
    when(dao.findByEmail("ghost@nowhere.example")).thenReturn(Optional.empty());

    assertThrows(InvalidRequestException.class, () -> service.getCurrentUser());
  }

  /**
   * BUG-ASSERT-CURRENT-USER-EMAIL-FALLBACK: assertCurrentUserEquals must pass for the
   * caller's *stored* username even when the raw principal name diverges (node keyed by
   * the OIDC sub while the token's preferred_username is "admin"). Resolving through
   * getCurrentUser()'s email fallback is what unblocks POST /users/{sub}/apikeys.
   */
  @Test
  public void assertCurrentUserEquals_passesForStoredUsername_whenRawPrincipalDiverges() {
    var storedNode = new User("uuid-service", "Admin", "User", "admin@demo.shepard.local");
    when(authCtx.getCurrentUserName()).thenReturn("admin");
    when(authCtx.getCurrentUserEmail()).thenReturn("admin@demo.shepard.local");
    when(dao.find("admin")).thenReturn(null);
    when(dao.findByEmail("admin@demo.shepard.local")).thenReturn(Optional.of(storedNode));

    // path param = the stored username (the node key) → must NOT throw
    service.assertCurrentUserEquals("uuid-service");
  }

  /**
   * assertCurrentUserEquals must still reject a path param that is neither the stored
   * username nor otherwise the caller's own identity (here: the raw preferred_username,
   * which is not the stored node key).
   */
  @Test
  public void assertCurrentUserEquals_throwsForNonStoredUsername() {
    var storedNode = new User("uuid-service", "Admin", "User", "admin@demo.shepard.local");
    when(authCtx.getCurrentUserName()).thenReturn("admin");
    when(authCtx.getCurrentUserEmail()).thenReturn("admin@demo.shepard.local");
    when(dao.find("admin")).thenReturn(null);
    when(dao.findByEmail("admin@demo.shepard.local")).thenReturn(Optional.of(storedNode));

    assertThrows(InvalidAuthException.class, () -> service.assertCurrentUserEquals("admin"));
  }
}
