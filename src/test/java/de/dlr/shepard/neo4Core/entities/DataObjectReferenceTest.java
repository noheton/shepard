package de.dlr.shepard.neo4Core.entities;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class DataObjectReferenceTest extends BaseTestCase {

	@Test
	public void equalsContract() {

		var a = new AbstractDataObject(1L) {
		};
		var b = new AbstractDataObject(2L) {
		};
		EqualsVerifier.simple().forClass(DataObjectReference.class)
				.withPrefabValues(DataObject.class, new DataObject(1L), new DataObject(2L))
				.withPrefabValues(User.class, new User("bob"), new User("claus"))
				.withPrefabValues(AbstractDataObject.class, a, b).verify();
	}

}
