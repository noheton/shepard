package de.dlr.shepard.neo4Core.entities;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class AbstractDataObjectTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(AbstractDataObject.class)
				.withPrefabValues(User.class, new User("bob"), new User("claus"))
				.withPrefabValues(DataObjectReference.class, new DataObjectReference(1L), new DataObjectReference(2L))
				.verify();
	}

}
