package de.dlr.shepard.common.neo4j.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class BasicEntityTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .forClass(BasicEntity.class)
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      .withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
      .verify();
  }

  @Test
  public void addAnnotationTest() {
    var annotation2 = new SemanticAnnotation(2L);
    var annotation3 = new SemanticAnnotation(3L);
    var entity = new Collection(1L);
    entity.addAnnotation(annotation2);
    entity.addAnnotation(annotation3);
    assertEquals(List.of(annotation2, annotation3), entity.getAnnotations());
  }
}
