package de.dlr.shepard.context.references.uri.entities;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.version.entities.Version;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class URIReferenceTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .forClass(URIReference.class)
      .withPrefabValues(DataObject.class, new DataObject(1L), new DataObject(2L))
      .withPrefabValues(Version.class, new Version("Version1"), new Version("Version2"))
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      .withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
      .verify();
  }
}
