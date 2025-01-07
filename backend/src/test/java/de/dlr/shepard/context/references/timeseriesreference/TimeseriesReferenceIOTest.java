package de.dlr.shepard.context.references.timeseriesreference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import java.util.Date;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class TimeseriesReferenceIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(TimeseriesReferenceIO.class).verify();
  }

  @Test
  public void testConversion() {
    var date = new Date();
    var user = new User("bob");
    var update = new Date();
    var updateUser = new User("claus");
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(500L);
    var container = new TimeseriesContainer(3L);
    var ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "name", "field");

    var timeseriesReference = new TimeseriesReference(1L);
    timeseriesReference.setShepardId(341L);
    timeseriesReference.setCreatedAt(date);
    timeseriesReference.setCreatedBy(user);
    timeseriesReference.setName("MyName");
    timeseriesReference.setUpdatedAt(update);
    timeseriesReference.setUpdatedBy(updateUser);
    timeseriesReference.setDataObject(dataObject);
    timeseriesReference.setEnd(213);
    timeseriesReference.setStart(123);
    timeseriesReference.setReferencedTimeseriesList(List.of(ts));
    timeseriesReference.setTimeseriesContainer(container);

    var converted = new TimeseriesReferenceIO(timeseriesReference);
    assertEquals(timeseriesReference.getShepardId(), converted.getId());
    assertEquals(timeseriesReference.getCreatedAt(), converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals(timeseriesReference.getName(), converted.getName());
    assertEquals(timeseriesReference.getUpdatedAt(), converted.getUpdatedAt());
    assertEquals("claus", converted.getUpdatedBy());
    assertEquals(dataObject.getShepardId(), converted.getDataObjectId());
    assertEquals(timeseriesReference.getEnd(), converted.getEnd());
    assertEquals(timeseriesReference.getStart(), converted.getStart());
    assertEquals(timeseriesReference.getReferencedTimeseriesList(), converted.getReferencedTimeseriesList());
    assertEquals(3L, converted.getTimeseriesContainerId());
  }

  @Test
  public void testConversion_ContainerNull() {
    var date = new Date();
    var user = new User("bob");
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(3485L);
    var ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "name", "field");

    var timeseriesReference = new TimeseriesReference(1L);
    timeseriesReference.setShepardId(234L);
    timeseriesReference.setCreatedAt(date);
    timeseriesReference.setCreatedBy(user);
    timeseriesReference.setName("MyName");
    timeseriesReference.setDataObject(dataObject);
    timeseriesReference.setEnd(213);
    timeseriesReference.setStart(123);
    timeseriesReference.setReferencedTimeseriesList(List.of(ts));

    var converted = new TimeseriesReferenceIO(timeseriesReference);
    assertEquals(timeseriesReference.getShepardId(), converted.getId());
    assertEquals(timeseriesReference.getCreatedAt(), converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals(timeseriesReference.getName(), converted.getName());
    assertEquals(dataObject.getShepardId(), converted.getDataObjectId());
    assertEquals(timeseriesReference.getEnd(), converted.getEnd());
    assertEquals(timeseriesReference.getStart(), converted.getStart());
    assertEquals(timeseriesReference.getReferencedTimeseriesList(), converted.getReferencedTimeseriesList());
    assertEquals(-1, converted.getTimeseriesContainerId());
  }
}
