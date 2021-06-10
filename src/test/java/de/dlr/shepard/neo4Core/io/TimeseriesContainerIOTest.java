package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.entities.User;
import nl.jqno.equalsverifier.EqualsVerifier;

public class TimeseriesContainerIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(TimeseriesContainerIO.class).verify();
	}

	@Test
	public void testConversion() {
		var user = new User("bob");
		var date = new Date();
		var update = new Date();
		var updateUser = new User("claus");

		var obj = new TimeseriesContainer(1L);
		obj.setCreatedAt(date);
		obj.setCreatedBy(user);
		obj.setDatabase("Database");
		obj.setName("name");
		obj.setUpdatedAt(update);
		obj.setUpdatedBy(updateUser);

		var converted = new TimeseriesContainerIO(obj);
		assertEquals(converted.getCreatedAt(), date);
		assertEquals(converted.getCreatedBy(), "bob");
		assertEquals(converted.getDatabase(), "Database");
		assertEquals(converted.getId(), 1L);
		assertEquals(converted.getName(), "name");
		assertEquals(converted.getUpdatedAt(), update);
		assertEquals(converted.getUpdatedBy(), "claus");
	}

}
