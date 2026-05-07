package de.dlr.shepard.data.file.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import java.util.Date;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class FileContainerTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .forClass(FileContainer.class)
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      .withPrefabValues(UserGroup.class, new UserGroup(1L), new UserGroup(2L))
      .withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
      .withPrefabValues(Collection.class, new Collection(3L), new Collection(4L))
      .withPrefabValues(Permissions.class, new Permissions(1L), new Permissions(2L))
      // appId is L2a-additive; not part of equals (legacy id remains canonical).
      .withIgnoredFields("appId")
      .verify();
  }

  @Test
  public void addFileTest() {
    var toAdd = new ShepardFile("newOid", new Date(), "filename", "md5");
    var container = new FileContainer(1L);
    container.addFile(toAdd);
    assertEquals(List.of(toAdd), container.getFiles());
  }
}
