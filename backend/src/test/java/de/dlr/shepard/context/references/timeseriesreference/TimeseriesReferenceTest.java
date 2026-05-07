package de.dlr.shepard.context.references.timeseriesreference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.version.entities.Version;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Mode;
import org.junit.jupiter.api.Test;

public class TimeseriesReferenceTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .set(Mode.skipMockito())
      .forClass(TimeseriesReference.class)
      .withPrefabValues(DataObject.class, new DataObject(1L), new DataObject(2L))
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      .withPrefabValues(Version.class, new Version("Version1"), new Version("Version2"))
      .withPrefabValues(UserGroup.class, new UserGroup(1L), new UserGroup(2L))
      .withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
      .withPrefabValues(Permissions.class, new Permissions(1L), new Permissions(2L))
      // appId is L2a-additive; not part of equals (legacy id remains canonical).
      .withIgnoredFields("appId")
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
