package de.dlr.shepard.neo4Core.entities;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class BasicContainerTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(BasicContainer.class)
				.withPrefabValues(User.class, new User("bob"), new User("claus"))
				.withPrefabValues(UserGroup.class, new UserGroup(1L), new UserGroup(2L))
				.withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
				.verify();
	}

}
