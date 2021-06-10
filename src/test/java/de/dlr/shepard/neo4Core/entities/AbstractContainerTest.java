package de.dlr.shepard.neo4Core.entities;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class AbstractContainerTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(AbstractContainer.class)
				.withPrefabValues(User.class, new User("bob"), new User("claus")).verify();
	}

}
