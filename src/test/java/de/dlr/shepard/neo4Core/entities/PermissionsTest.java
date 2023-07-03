package de.dlr.shepard.neo4Core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.util.PermissionType;
import nl.jqno.equalsverifier.EqualsVerifier;

public class PermissionsTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(Permissions.class)
				.withPrefabValues(User.class, new User("bob"), new User("claus"))
				.withPrefabValues(BasicEntity.class, new Collection(5L), new Collection(6L))
				.withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
				.verify();
	}

	@Test
	public void constructorTest() {
		User owner = new User("bob");
		List<User> reader = List.of(new User("reader"));
		List<User> writer = List.of(new User("reader"));
		List<User> manager = List.of(new User("reader"));
		List<UserGroup> readerGroups = List.of(new UserGroup(1L));
		List<UserGroup> writerGroups = List.of(new UserGroup(2L));
		Permissions expected = new Permissions() {
			{
				setOwner(owner);
				setReader(reader);
				setWriter(writer);
				setReaderGroups(readerGroups);
				setWriterGroups(writerGroups);
				setManager(manager);
				setPermissionType(PermissionType.PublicReadable);
			}
		};
		Permissions actual = new Permissions(owner, reader, writer, readerGroups, writerGroups, manager,
				PermissionType.PublicReadable);

		assertEquals(expected, actual);
	}

	@Test
	public void entityConstructorTest() {
		var entity = new Collection(1L);
		var user = new User("bob");
		var expected = new Permissions() {
			{
				setEntity(entity);
				setOwner(user);
				setPermissionType(PermissionType.Public);
			}
		};
		var actual = new Permissions(entity, user, PermissionType.Public);

		assertEquals(expected, actual);
	}

	@Test
	public void getUniqueIdTest() {
		var perm = new Permissions(5L);
		assertEquals("5", perm.getUniqueId());
	}

	@Test
	public void simpleConstructorTest() {
		var perm1 = new Permissions();
		perm1.setId(5L);
		var perm2 = new Permissions(5L);
		assertEquals(perm1, perm2);
	}
}
