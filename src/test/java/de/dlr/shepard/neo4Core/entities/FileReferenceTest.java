package de.dlr.shepard.neo4Core.entities;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class FileReferenceTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(FileReference.class)
				.withPrefabValues(DataObject.class, new DataObject(1L), new DataObject(2L))
				.withPrefabValues(User.class, new User("bob"), new User("claus")).verify();
	}

}
