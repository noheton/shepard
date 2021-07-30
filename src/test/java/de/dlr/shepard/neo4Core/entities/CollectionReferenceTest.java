package de.dlr.shepard.neo4Core.entities;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class CollectionReferenceTest extends BaseTestCase {

	@Test
	public void equalsContract() {

		EqualsVerifier.simple().forClass(CollectionReference.class)
				.withPrefabValues(DataObject.class, new DataObject(1L), new DataObject(2L))
				.withPrefabValues(User.class, new User("bob"), new User("claus"))
				.withPrefabValues(Collection.class, new Collection(1L), new Collection(2L)).verify();
	}

}
