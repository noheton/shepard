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
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.subscription.entities.Subscription;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@QuarkusComponentTest
public class UserServiceTest {

  @InjectMock
  UserDAO dao;

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
}
