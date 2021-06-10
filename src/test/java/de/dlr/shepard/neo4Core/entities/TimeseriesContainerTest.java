package de.dlr.shepard.neo4Core.entities;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class TimeseriesContainerTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(TimeseriesContainer.class)
				.withPrefabValues(User.class, new User("bob"), new User("claus")).verify();
	}

}
