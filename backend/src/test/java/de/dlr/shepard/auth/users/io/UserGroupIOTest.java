package de.dlr.shepard.auth.users.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import java.util.ArrayList;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class UserGroupIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(UserGroupIO.class).verify();
  }

  @Test
  public void testConversion() {
    UserGroup group = new UserGroup();
    group.setName("group");
    group.setId(1L);
    User user = new User("AKP");
    ArrayList<User> users = new ArrayList<>();
    users.add(user);
    group.setUsers(users);
    var converted = new UserGroupIO(group);
    assertEquals(1L, converted.getId());
    assertEquals("group", converted.getName());
    assertEquals(1, converted.getUsernames().length);
    assertEquals("AKP", converted.getUsernames()[0]);
  }
}
