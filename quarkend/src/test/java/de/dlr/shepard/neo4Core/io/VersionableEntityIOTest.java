package de.dlr.shepard.neo4Core.io;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.VersionableEntity;

public class VersionableEntityIOTest extends BaseTestCase {

	private static class EntityIO extends VersionableEntityIO {

		public EntityIO(VersionableEntity entity) {
			super(entity);
		}

	}

	@Test
	public void testConversion() {
		var date = new Date();
		var user = new User("bob");
		var update = new Date();
		var updateUser = new User("claus");

		var obj = new BasicReference(1L);
		obj.setShepardId(2L);
		obj.setCreatedAt(date);
		obj.setCreatedBy(user);
		obj.setName("MyName");
		obj.setUpdatedAt(update);
		obj.setUpdatedBy(updateUser);

		var converted = new EntityIO(obj);
		assertEquals(obj.getShepardId(), converted.getId());
		assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
		assertEquals(obj.getCreatedBy().getUsername(), converted.getCreatedBy());
		assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
		assertEquals(obj.getUpdatedBy().getUsername(), converted.getUpdatedBy());
		assertEquals(obj.getName(), converted.getName());
	}

	@Test
	public void testConversion_userNull() {
		var obj = new BasicReference(1L);
		obj.setShepardId(2L);

		var converted = new EntityIO(obj);
		assertEquals(obj.getShepardId(), converted.getId());
		assertNull(converted.getCreatedBy());
		assertNull(converted.getUpdatedBy());
	}

	@Test
	public void getUniqueIdTest() {
		var obj = new BasicReference(1L);
		obj.setShepardId(2L);
		var converted = new EntityIO(obj);
		var actual = converted.getUniqueId();
		assertEquals(obj.getShepardId().toString(), actual);
	}

}
