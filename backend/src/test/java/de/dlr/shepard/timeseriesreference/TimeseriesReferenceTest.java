package de.dlr.shepard.timeseriesreference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.SemanticAnnotation;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.timeseriesreference.model.TimeseriesReference;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class TimeseriesReferenceTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .forClass(TimeseriesReference.class)
      .withPrefabValues(DataObject.class, new DataObject(1L), new DataObject(2L))
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      .withPrefabValues(UserGroup.class, new UserGroup(1L), new UserGroup(2L))
      .withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
      .verify();
  }

  @Test
  public void addTimeseriesTest() {
    var ref = new TimeseriesReference(1L);
    var ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symname", "field");
    ref.addTimeseries(ts);

    assertEquals(List.of(ts), ref.getReferencedTimeseriesList());
  }
}
