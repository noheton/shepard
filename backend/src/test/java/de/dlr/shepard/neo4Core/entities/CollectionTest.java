package de.dlr.shepard.neo4Core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class CollectionTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(Collection.class)
				.withPrefabValues(DataObject.class, new DataObject(1L), new DataObject(2L))
				.withPrefabValues(User.class, new User("bob"), new User("claus"))
				.withPrefabValues(UserGroup.class, new UserGroup(1L), new UserGroup(2L))
				.withPrefabValues(CollectionReference.class, new CollectionReference(1L), new CollectionReference(2L))
				.withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
				.withPrefabValues(Version.class, new Version(new UUID(1L, 2L)), new Version(new UUID(3L, 4L))).verify();
	}

	@Test
	public void copyConstructorTest() {
		Collection coll = new Collection();
		coll.setAnnotations(null);
		coll.setAttributes(null);
		coll.setCreatedAt(new Date(100L));
		coll.setCreatedBy(new User("karl"));
		coll.setDataObjects(null);
		coll.setDeleted(false);
		coll.setDescription("description");
		coll.setIncoming(null);
		coll.setName("name");
		coll.setPermissions(null);
		coll.setShepardId(20L);
		coll.setVersion(null);
		Collection copy = new Collection(coll);
		assertEquals(coll, copy);
	}

	@Test
	public void addDataObjectTest() {
		var col = new Collection(1L);
		var dataObject = new DataObject(2L);
		col.addDataObject(dataObject);

		assertEquals(col.getDataObjects(), List.of(dataObject));
	}

}
