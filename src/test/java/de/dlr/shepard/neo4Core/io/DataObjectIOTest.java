package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

public class DataObjectIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(DataObjectIO.class).verify();
	}

	@Test
	public void testConversion() {
		var date = new Date();
		var user = new User("bob");
		var update = new Date();
		var updateUser = new User("claus");
		var incoming = new DataObjectReference(7L);
		var parent = new DataObject(2L);
		var child = new DataObject(3L);
		var suc = new DataObject(4L);
		var pre = new DataObject(5L);
		var col = new Collection(6L);

		var obj = new DataObject(1L);
		obj.setAttributes(Map.of("a", "b", "c", "1"));
		obj.setCreatedAt(date);
		obj.setCreatedBy(user);
		obj.setDescription("My Description");
		obj.setIncoming(List.of(incoming));
		obj.setName("MyName");
		obj.setUpdatedAt(update);
		obj.setUpdatedBy(updateUser);
		obj.setParent(parent);
		obj.setChildren(List.of(child));
		obj.setPredecessors(List.of(pre));
		obj.setSuccessors(List.of(suc));
		obj.setCollection(col);

		var converted = new DataObjectIO(obj);
		assertEquals(obj.getId(), converted.getId());
		assertEquals(obj.getAttributes(), converted.getAttributes());
		assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
		assertEquals("bob", converted.getCreatedBy());
		assertEquals(obj.getDescription(), converted.getDescription());
		assertEquals("[7]", Arrays.toString(converted.getIncomingIds()));
		assertEquals(obj.getName(), converted.getName());
		assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
		assertEquals("claus", converted.getUpdatedBy());
		assertEquals(2L, converted.getParentId());
		assertEquals(6L, converted.getCollectionId());
		assertEquals("[3]", Arrays.toString(converted.getChildrenIds()));
		assertEquals("[4]", Arrays.toString(converted.getSuccessorIds()));
		assertEquals("[5]", Arrays.toString(converted.getPredecessorIds()));
	}

	@Test
	public void testConversionNoParent() {
		var col = new Collection(2L);
		var obj = new DataObject(1L);
		obj.setCollection(col);

		var converted = new DataObjectIO(obj);
		assertNull(converted.getParentId());
	}

}
