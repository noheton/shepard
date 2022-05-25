package de.dlr.shepard.neo4Core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.mongoDB.ShepardFile;
import nl.jqno.equalsverifier.EqualsVerifier;

public class FileReferenceTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(FileReference.class)
				.withPrefabValues(DataObject.class, new DataObject(1L), new DataObject(2L))
				.withPrefabValues(User.class, new User("bob"), new User("claus")).verify();
	}

	@Test
	public void addFilesTest() {
		var ref = new FileReference(1L);
		var file = new ShepardFile("oid", new Date(), "name", "md5");
		ref.addFile(file);

		assertEquals(List.of(file), ref.getFiles());
	}

}
