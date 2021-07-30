package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.CollectionReference;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
import nl.jqno.equalsverifier.EqualsVerifier;

public class CollectionReferenceIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(CollectionReferenceIO.class).verify();
	}

	@Test
	public void testConversion() {
		var date = new Date();
		var user = new User("bob");
		var update = new Date();
		var updateUser = new User("claus");
		var dataObject = new DataObject(2L);
		var referenced = new Collection(3L);

		var obj = new CollectionReference(1L);
		obj.setCreatedAt(date);
		obj.setCreatedBy(user);
		obj.setName("MyName");
		obj.setUpdatedAt(update);
		obj.setUpdatedBy(updateUser);
		obj.setDataObject(dataObject);
		obj.setReferencedCollection(referenced);
		obj.setRelationship("TestRel");

		var converted = new CollectionReferenceIO(obj);
		assertEquals(obj.getId(), converted.getId());
		assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
		assertEquals("bob", converted.getCreatedBy());
		assertEquals(obj.getName(), converted.getName());
		assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
		assertEquals("claus", converted.getUpdatedBy());
		assertEquals(2L, converted.getDataObjectId());
		assertEquals(3L, converted.getReferencedCollectionId());
		assertEquals(obj.getRelationship(), converted.getRelationship());
	}

	@Test
	public void testConversion_Deleted() {
		var dataObject = new DataObject(2L);

		var obj = new CollectionReference(1L);
		obj.setDataObject(dataObject);
		obj.setReferencedCollection(null);
		obj.setRelationship("TestRel");

		var converted = new CollectionReferenceIO(obj);
		assertEquals(1L, converted.getId());
		assertEquals(2L, converted.getDataObjectId());
		assertEquals(-1, converted.getReferencedCollectionId());
		assertEquals(obj.getRelationship(), converted.getRelationship());
	}

}
