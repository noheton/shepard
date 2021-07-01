package de.dlr.shepard.neo4Core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.mongoDB.File;
import nl.jqno.equalsverifier.EqualsVerifier;

public class FileContainerTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(FileContainer.class)
				.withPrefabValues(User.class, new User("bob"), new User("claus")).verify();
	}

	@Test
	public void addFileTest() {
		var toAdd = new File("newOid", "filename");
		var container = new FileContainer(1L);
		container.addFile(toAdd);
		assertEquals(List.of(toAdd), container.getFiles());
	}

}
