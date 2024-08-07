package de.dlr.shepard.neo4Core.entities;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class TimeseriesContainerTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .forClass(TimeseriesContainer.class)
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      .withPrefabValues(UserGroup.class, new UserGroup(1L), new UserGroup(2L))
      .withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
      .verify();
  }
}
