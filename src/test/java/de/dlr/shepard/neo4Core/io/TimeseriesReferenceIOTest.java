package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.entities.TimeseriesReference;
import de.dlr.shepard.neo4Core.entities.User;
import nl.jqno.equalsverifier.EqualsVerifier;

public class TimeseriesReferenceIOTest extends BaseTestCase {

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
		var container = new TimeseriesContainer(3L);
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");

		var obj = new TimeseriesReference(1L);
		obj.setCreatedAt(date);
		obj.setCreatedBy(user);
		obj.setName("MyName");
		obj.setUpdatedAt(update);
		obj.setUpdatedBy(updateUser);
		obj.setDataObject(dataObject);
		obj.setEnd(213);
		obj.setStart(123);
		obj.setTimeseries(List.of(ts));
		obj.setTimeseriesContainer(container);

		var converted = new TimeseriesReferenceIO(obj);
		assertEquals(obj.getId(), converted.getId());
		assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
		assertEquals("bob", converted.getCreatedBy());
		assertEquals(obj.getName(), converted.getName());
		assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
		assertEquals("claus", converted.getUpdatedBy());
		assertEquals(2L, converted.getDataObjectId());
		assertEquals(obj.getEnd(), converted.getEnd());
		assertEquals(obj.getStart(), converted.getStart());
		assertEquals(obj.getTimeseries(), converted.getTimeseries());
		assertEquals(3L, converted.getTimeseriesContainerId());
	}

	@Test
	public void testConversion_ContainerNull() {
		var date = new Date();
		var user = new User("bob");
		var dataObject = new DataObject(2L);
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");

		var obj = new TimeseriesReference(1L);
		obj.setCreatedAt(date);
		obj.setCreatedBy(user);
		obj.setName("MyName");
		obj.setDataObject(dataObject);
		obj.setEnd(213);
		obj.setStart(123);
		obj.setTimeseries(List.of(ts));

		var converted = new TimeseriesReferenceIO(obj);
		assertEquals(obj.getId(), converted.getId());
		assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
		assertEquals("bob", converted.getCreatedBy());
		assertEquals(obj.getName(), converted.getName());
		assertEquals(2L, converted.getDataObjectId());
		assertEquals(obj.getEnd(), converted.getEnd());
		assertEquals(obj.getStart(), converted.getStart());
		assertEquals(obj.getTimeseries(), converted.getTimeseries());
		assertEquals(-1, converted.getTimeseriesContainerId());
	}

}
