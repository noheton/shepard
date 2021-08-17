package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import de.dlr.shepard.neo4Core.entities.User;
import nl.jqno.equalsverifier.EqualsVerifier;

public class StructuredDataReferenceIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(StructuredDataReferenceIO.class).verify();
	}

	@Test
	public void testConversion() {
		var date = new Date();
		var user = new User("bob");
		var update = new Date();
		var updateUser = new User("claus");
		var dataObject = new DataObject(2L);
		var container = new StructuredDataContainer(3L);
		var structuredData = new StructuredData("oid");

		var obj = new StructuredDataReference(1L);
		obj.setCreatedAt(date);
		obj.setCreatedBy(user);
		obj.setName("MyName");
		obj.setUpdatedAt(update);
		obj.setUpdatedBy(updateUser);
		obj.setDataObject(dataObject);
		obj.setStructuredDataContainer(container);
		obj.setStructuredDatas(List.of(structuredData));
		String[] oids = obj.getStructuredDatas().stream().map(sd -> sd.getOid()).toArray(String[]::new);

		var converted = new StructuredDataReferenceIO(obj);
		assertEquals(obj.getId(), converted.getId());
		assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
		assertEquals("bob", converted.getCreatedBy());
		assertEquals(obj.getName(), converted.getName());
		assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
		assertEquals("claus", converted.getUpdatedBy());
		assertEquals(2L, converted.getDataObjectId());
		assertEquals(3L, converted.getStructuredDataContainerId());
		assertTrue(Arrays.equals(oids, converted.getStructuredDataOids()));
	}

	@Test
	public void testConversion_ContainerNull() {
		var date = new Date();
		var user = new User("bob");
		var dataObject = new DataObject(2L);
		var structuredData = new StructuredData("oid");

		var obj = new StructuredDataReference(1L);
		obj.setCreatedAt(date);
		obj.setCreatedBy(user);
		obj.setName("MyName");
		obj.setDataObject(dataObject);
		obj.setStructuredDatas(List.of(structuredData));
		String[] oids = obj.getStructuredDatas().stream().map(sd -> sd.getOid()).toArray(String[]::new);

		var converted = new StructuredDataReferenceIO(obj);
		assertEquals(obj.getId(), converted.getId());
		assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
		assertEquals("bob", converted.getCreatedBy());
		assertEquals(obj.getName(), converted.getName());
		assertEquals(2L, converted.getDataObjectId());
		assertEquals(-1, converted.getStructuredDataContainerId());
		assertTrue(Arrays.equals(oids, converted.getStructuredDataOids()));
	}

}
