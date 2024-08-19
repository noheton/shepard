package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.ApiKey;
import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.entities.User;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
    var actual = service.createUser(user);
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

    var actual = service.updateUser(user);
    verify(dao, never()).createOrUpdate(any(User.class));
    assertEquals(old, actual);
  }

  @Test
  public void updateUserTest_noUser() {
    var user = new User("bob", "John", "Doe", "john.doe@example.com");

    when(dao.find("bob")).thenReturn(null);
    when(dao.createOrUpdate(user)).thenReturn(user);

    var actual = service.updateUser(user);
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

    var actual = service.updateUser(user);
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

    var actual = service.updateUser(user);
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

    var actual = service.updateUser(user);
    assertEquals(expected, actual);
  }

  @Test
  public void updateUserTest_emptyInput() {
    var uid = UUID.randomUUID();
    var old = new User("bob", "John", "Doe", "john.doe@example.com");
    old.setApiKeys(List.of(new ApiKey(uid)));
    old.setSubscriptions(List.of(new Subscription(3L)));
    var user = new User("bob");

    when(dao.find("bob")).thenReturn(old);

    var actual = service.updateUser(user);
    assertEquals(old, actual);
  }
}
