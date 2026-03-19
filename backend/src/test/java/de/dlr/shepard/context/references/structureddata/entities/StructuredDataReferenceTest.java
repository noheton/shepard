package de.dlr.shepard.context.references.structureddata.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import java.util.Date;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Mode;
import org.junit.jupiter.api.Test;

public class StructuredDataReferenceTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .set(Mode.skipMockito())
      .forClass(StructuredDataReference.class)
      .withPrefabValues(DataObject.class, new DataObject(1L), new DataObject(2L))
      .withPrefabValues(Version.class, new Version("Version1"), new Version("Version2"))
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      .withPrefabValues(UserGroup.class, new UserGroup(1L), new UserGroup(2L))
      .withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
      .withPrefabValues(Permissions.class, new Permissions(1L), new Permissions(2L))
      .verify();
  }

  @Test
  public void addStructuredDataTest() {
    var ref = new StructuredDataReference(1L);
    var sd = new StructuredData("newOid", new Date(), "name");
    ref.addStructuredData(sd);

    assertEquals(List.of(sd), ref.getStructuredDatas());
  }
}
