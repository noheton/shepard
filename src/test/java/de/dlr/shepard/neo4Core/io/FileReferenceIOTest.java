package de.dlr.shepard.neo4Core.io;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class FileReferenceIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(FileReferenceIO.class).verify();
	}

	/*
	 * @Test public void testConversion() { var date = new Date(); var user = new
	 * User("bob"); var update = new Date(); var updateUser = new User("claus"); var
	 * dataObject = new DataObject(2L); var container = new FileContainer(3L); var
	 * file = new File("oid", "name");
	 * 
	 * var obj = new FileReference(1L); obj.setCreatedAt(date);
	 * obj.setCreatedBy(user); obj.setName("MyName"); obj.setUpdatedAt(update);
	 * obj.setUpdatedBy(updateUser); obj.setDataObject(dataObject);
	 * obj.setFilecontainer(container); obj.setFiles(List.of(file));
	 * 
	 * var converted = new FileReferenceIO(obj); assertEquals(obj.getId(),
	 * converted.getId()); assertEquals(obj.getCreatedAt(),
	 * converted.getCreatedAt()); assertEquals("bob", converted.getCreatedBy());
	 * assertEquals(obj.getName(), converted.getName());
	 * assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
	 * assertEquals("claus", converted.getUpdatedBy()); assertEquals(2L,
	 * converted.getDataObjectId()); assertEquals(3L,
	 * converted.getFilecontainerId()); assertEquals(obj.getFiles(),
	 * converted.getFiles()); }
	 */

}
