package de.dlr.shepard.context.collection.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.context.references.dataobject.entities.CollectionReference;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.data.semantic.entities.SemanticAnnotation;
import java.util.List;
import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class CollectionTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .forClass(Collection.class)
      .withPrefabValues(DataObject.class, new DataObject(1L), new DataObject(2L))
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      .withPrefabValues(UserGroup.class, new UserGroup(1L), new UserGroup(2L))
      .withPrefabValues(CollectionReference.class, new CollectionReference(1L), new CollectionReference(2L))
      .withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
      .withPrefabValues(Version.class, new Version(new UUID(1L, 2L)), new Version(new UUID(3L, 4L)))
      .verify();
  }

  @Test
  public void addDataObjectTest() {
    var col = new Collection(1L);
    var dataObject = new DataObject(2L);
    col.addDataObject(dataObject);

    assertEquals(col.getDataObjects(), List.of(dataObject));
  }
}
