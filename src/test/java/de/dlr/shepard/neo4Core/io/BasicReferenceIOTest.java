package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
import nl.jqno.equalsverifier.EqualsVerifier;

public class BasicReferenceIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(BasicReferenceIO.class).verify();
	}

	// TODO: maybe replace hardcoded names by .getUsername
	@Test
	public void testConversion() {
		var date = new Date();
		var user = new User("bob");
		var update = new Date();
		var updateUser = new User("claus");
		var dataObject = new DataObject(2L);
		dataObject.setShepardId(4L);

		var obj = new BasicReference(1L);
		obj.setCreatedAt(date);
		obj.setCreatedBy(user);
		obj.setName("MyName");
		obj.setUpdatedAt(update);
		obj.setUpdatedBy(updateUser);
		obj.setDataObject(dataObject);
		obj.setShepardId(4L);

		var converted = new BasicReferenceIO(obj);
		assertEquals(obj.getShepardId(), converted.getId());
		assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
		assertEquals("bob", converted.getCreatedBy());
		assertEquals(obj.getName(), converted.getName());
		assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
		assertEquals("claus", converted.getUpdatedBy());
		assertEquals(dataObject.getShepardId(), converted.getDataObjectId());
	}

}
