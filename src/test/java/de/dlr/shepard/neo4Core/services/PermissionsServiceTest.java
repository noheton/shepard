package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.UserGroupDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.neo4Core.io.PermissionsIO;

public class PermissionsServiceTest extends BaseTestCase {

	@Mock
	private UserDAO userDAO;

	@Mock
	private UserGroupDAO userGroupDAO;

	@Mock
	private PermissionsDAO dao;

	@InjectMocks
	private PermissionsService service;

	@Test
	public void createPermissionsTest() {
		var col = new Collection(2L);
		var perms = new Permissions();

		var created = new Permissions(1L);
		created.setEntity(col);

		when(dao.createWithEntity(perms, 2L)).thenReturn(created);

		var actual = service.createPermissions(2L);
		assertEquals(created, actual);
	}

	@Test
	public void getPermissionsTest() {
		var col = new Collection(2L);
		var perms = new Permissions(1L);
		perms.setEntity(col);

		when(dao.findByEntity(2L)).thenReturn(perms);
		var actual = service.getPermissionsByEntity(2L);
		assertEquals(perms, actual);
	}

	@Test
	public void getPermissionsTest_notFound() {
		when(dao.findByEntity(2L)).thenReturn(null);

		var actual = service.getPermissionsByEntity(2L);
		assertNull(actual);
		verify(dao).findByEntity(2L);
	}

	@Test
	public void updatePermissionsTest() {
		var owner = new User("owner");
		var reader = new User("reader");
		var writer = new User("writer");
		var manager = new User("manager");
		var groupwriter = new User("groupwriter");
		UserGroup writerGroup = new UserGroup();
		ArrayList<User> writerGroupList = new ArrayList<User>();
		writerGroupList.add(groupwriter);
		writerGroup.setName("writerGroup");
		writerGroup.setId(12L);
		writerGroup.setUsers(writerGroupList);
		List<UserGroup> writerGroupsList = new ArrayList<UserGroup>();
		writerGroupsList.add(writerGroup);

		UserGroup readerGroup = new UserGroup();
		readerGroup.setId(null);
		ArrayList<UserGroup> readerGroupsList = new ArrayList<UserGroup>();
		readerGroupsList.add(readerGroup);

		var col = new Collection(2L);
		var existing = new Permissions(1L);
		existing.setEntity(col);

		var perms = new PermissionsIO() {
			{
				setOwner("owner");
				setReader(new String[] { "reader" });
				setWriter(new String[] { "writer" });
				setWriterGroupIds(new long[] { 12L });
				setManager(new String[] { "manager" });
			}
		};

		var updated = new Permissions() {
			{
				setId(1L);
				setEntity(col);
				setOwner(owner);
				setReader(List.of(reader));
				setWriter(List.of(writer));
				setWriterGroups(writerGroupsList);
				setManager(List.of(manager));
			}
		};

		when(userDAO.find("owner")).thenReturn(owner);
		when(userDAO.find("reader")).thenReturn(reader);
		when(userDAO.find("writer")).thenReturn(writer);
		when(userDAO.find("manager")).thenReturn(manager);
		when(userGroupDAO.find(12L)).thenReturn(writerGroup);
		when(dao.findByEntity(2L)).thenReturn(existing);
		when(dao.createOrUpdate(updated)).thenReturn(updated);
		var actual = service.updatePermissions(perms, 2L);
		assertEquals(updated, actual);
	}

	@Test
	public void updatePermissionsTest_oldIsNull() {
		var owner = new User("owner");
		var reader = new User("reader");
		var writer = new User("writer");
		var manager = new User("manager");

		var col = new Collection(2L);
		var perms = new PermissionsIO() {
			{
				setOwner("owner");
				setReader(new String[] { "reader" });
				setWriter(new String[] { "writer" });
				setReaderGroupIds(new long[] {});
				setManager(new String[] { "manager" });
			}
		};

		var toCreate = new Permissions() {
			{
				setOwner(owner);
				setReader(List.of(reader));
				setWriter(List.of(writer));
				setReaderGroups(Collections.emptyList());
				setWriterGroups(Collections.emptyList());
				setManager(List.of(manager));
			}
		};

		var updated = new Permissions() {
			{
				setId(1L);
				setEntity(col);
				setOwner(owner);
				setReader(List.of(reader));
				setWriter(List.of(writer));
				setReaderGroups(Collections.emptyList());
				setWriterGroups(Collections.emptyList());
				setManager(List.of(manager));
			}
		};

		when(userDAO.find("owner")).thenReturn(owner);
		when(userDAO.find("reader")).thenReturn(reader);
		when(userDAO.find("writer")).thenReturn(writer);
		when(userDAO.find("manager")).thenReturn(manager);
		when(dao.findByEntity(2L)).thenReturn(null);
		when(dao.createWithEntity(toCreate, 2L)).thenReturn(updated);

		var actual = service.updatePermissions(perms, 2L);
		assertEquals(updated, actual);
	}

	@Test
	public void updatePermissionsTest_userIsNull() {
		var reader = new User("reader");
		var writer = new User("writer");
		var manager = new User("manager");

		var col = new Collection(2L);
		var existing = new Permissions(1L);
		existing.setEntity(col);

		var perms = new PermissionsIO() {
			{
				setReader(new String[] { "reader", "not_existing" });
				setWriter(new String[] { "writer", null });
				setReaderGroupIds(new long[] {});
				setWriterGroupIds(new long[] {});
				setManager(new String[0]);
			}
		};

		var updated = new Permissions() {
			{
				setId(1L);
				setEntity(col);
				setReader(List.of(reader));
				setWriter(List.of(writer));
				setReaderGroups(Collections.emptyList());
				setWriterGroups(Collections.emptyList());
			}
		};

		when(userDAO.find("reader")).thenReturn(reader);
		when(userDAO.find("writer")).thenReturn(writer);
		when(userDAO.find("manager")).thenReturn(manager);
		when(dao.findByEntity(2L)).thenReturn(existing);
		when(dao.createOrUpdate(updated)).thenReturn(updated);

		var actual = service.updatePermissions(perms, 2L);
		assertEquals(updated, actual);
	}

}
