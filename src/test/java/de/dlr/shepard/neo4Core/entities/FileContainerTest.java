package de.dlr.shepard.neo4Core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.mongoDB.ShepardFile;
import nl.jqno.equalsverifier.EqualsVerifier;

public class FileContainerTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(FileContainer.class)
				.withPrefabValues(User.class, new User("bob"), new User("claus")).verify();
	}

	@Test
	public void addFileTest() {
		var toAdd = new ShepardFile("newOid", new Date(), "filename", "md5");
		var container = new FileContainer(1L);
		container.addFile(toAdd);
		assertEquals(List.of(toAdd), container.getFiles());
	}

}
