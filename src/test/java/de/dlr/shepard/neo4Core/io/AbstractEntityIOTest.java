package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class AbstractEntityIOTest extends BaseTestCase {

	private class EntityIO extends AbstractEntityIO {

		public EntityIO(long id) {
			this.setId(id);
		}

	}

	@Test
	public void getUniqueIdTest() {
		var entity = new EntityIO(2L);
		var actual = entity.getUniqueId();

		assertEquals("2", actual);
	}

}
