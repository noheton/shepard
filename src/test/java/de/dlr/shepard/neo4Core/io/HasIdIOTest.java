package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.util.HasId;
import nl.jqno.equalsverifier.EqualsVerifier;

public class HasIdIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(HasIdIO.class).verify();
	}

	@Test
	public void testConversion() {
		HasId hasId = new HasId() {

			@Override
			public String getUniqueId() {
				return "unique_id";
			}
		};

		var converted = new HasIdIO(hasId);
		assertEquals(hasId.getUniqueId(), converted.getUniqueId());
	}

}
