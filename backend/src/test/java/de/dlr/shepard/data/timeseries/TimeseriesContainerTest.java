package de.dlr.shepard.data.timeseries;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
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
