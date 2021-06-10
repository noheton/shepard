package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.DataObjectReference;
import de.dlr.shepard.neo4Core.entities.User;
import nl.jqno.equalsverifier.EqualsVerifier;

public class CollectionIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(CollectionIO.class).verify();
	}

	@Test
	public void testConversion() {
		var dataObject = new DataObject(2L);
		var date = new Date();
		var user = new User("bob");
		var update = new Date();
		var updateUser = new User("claus");
		var incoming = new DataObjectReference(3L);

		var obj = new Collection(1L);
		obj.setAttributes(Map.of("a", "b", "c", "1"));
		obj.setCreatedAt(date);
		obj.setCreatedBy(user);
		obj.setDataObjects(List.of(dataObject));
		obj.setDescription("My Description");
		obj.setIncoming(List.of(incoming));
		obj.setName("MyName");
		obj.setUpdatedAt(update);
		obj.setUpdatedBy(updateUser);

		var converted = new CollectionIO(obj);
		assertEquals(obj.getId(), converted.getId());
		assertEquals(obj.getAttributes(), converted.getAttributes());
		assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
		assertEquals("bob", converted.getCreatedBy());
		assertEquals("[2]", Arrays.toString(converted.getDataObjectIds()));
		assertEquals(obj.getDescription(), converted.getDescription());
		assertEquals("[3]", Arrays.toString(converted.getIncomingIds()));
		assertEquals(obj.getName(), converted.getName());
		assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
		assertEquals("claus", converted.getUpdatedBy());
	}

}
