package de.dlr.shepard.context.semantic.entities;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.context.collection.entities.DataObject;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class SemanticAnnotationTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .forClass(SemanticAnnotation.class)
      .withPrefabValues(BasicEntity.class, new DataObject(1L), new DataObject(2L))
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      .withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
      .verify();
  }
}
