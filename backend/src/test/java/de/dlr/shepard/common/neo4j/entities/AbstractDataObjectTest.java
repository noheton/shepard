package de.dlr.shepard.common.neo4j.entities;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import de.dlr.shepard.data.semantic.entities.SemanticAnnotation;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class AbstractDataObjectTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .forClass(AbstractDataObject.class)
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      .withPrefabValues(DataObjectReference.class, new DataObjectReference(1L), new DataObjectReference(2L))
      .withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
      .verify();
  }
}
