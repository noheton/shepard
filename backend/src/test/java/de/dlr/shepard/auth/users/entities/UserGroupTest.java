package de.dlr.shepard.auth.users.entities;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import java.util.ArrayList;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class UserGroupTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    ArrayList<User> users1 = new ArrayList<>();
    User user1 = new User("user1");
    user1.setApiKeys(null);
    user1.setSubscriptions(null);
    users1.add(user1);
    ArrayList<User> users2 = new ArrayList<>();
    User user2 = new User("user2");
    user2.setApiKeys(null);
    user2.setSubscriptions(null);
    users2.add(new User("user2"));
    User user3 = new User("user3");
    User user4 = new User("user4");
    EqualsVerifier.simple()
      .forClass(UserGroup.class)
      .withPrefabValues(String.class, "group1", "group2")
      .withPrefabValues(Long.class, 1L, 2L)
      .withPrefabValues(User.class, user3, user4)
      .withPrefabValues(Permissions.class, new Permissions(1L), new Permissions(2L))
      .withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
      .verify();
  }
}
