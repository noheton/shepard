package de.dlr.shepard.neo4Core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class AbstractDataObjectTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(AbstractDataObject.class)
				.withPrefabValues(User.class, new User("bob"), new User("claus"))
				.withPrefabValues(DataObjectReference.class, new DataObjectReference(1L), new DataObjectReference(2L))
				.verify();
	}

	@Test
	public void addIncomingTest() {
		var incoming = new DataObjectReference(2L);

		var dataObject = new AbstractDataObject() {
			{
				setId(1L);
			}
		};

		dataObject.addIncoming(incoming);
		assertEquals(List.of(incoming), dataObject.getIncoming());
	}

}
