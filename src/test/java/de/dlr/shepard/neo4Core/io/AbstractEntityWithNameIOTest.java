package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.AbstractEntity;
import de.dlr.shepard.neo4Core.entities.Collection;

public class AbstractEntityWithNameIOTest extends BaseTestCase {

	private static class EntityIO extends AbstractEntityWithNameIO {

		public EntityIO(AbstractEntity entity) {
			super(entity);
		}

	}

	@Test
	public void testConversion() {
		var obj = new Collection(1L);
		obj.setName("test");

		var converted = new EntityIO(obj);
		assertEquals("test", converted.getName());
	}

}
