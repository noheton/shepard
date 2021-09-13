package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.BasicReferenceDAO;
import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.DataObjectReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.QueryParamHelper;

public class DataObjectServiceTest extends BaseTestCase {

	@Mock
	private DataObjectDAO dao;

	@Mock
	private CollectionDAO collectionDAO;

	@Mock
	private BasicReferenceDAO referenceDAO;

	@Mock
	private UserDAO userDAO;

	@Mock
	private DateHelper dateHelper;

	@InjectMocks
	private DataObjectService service;

	@Test
	public void getDataObjectTest() {
		DataObject dataObject = new DataObject(5L);
		when(dao.find(5L)).thenReturn(dataObject);
		DataObject returned = service.getDataObject(5L);
		assertEquals(dataObject, returned);
	}

	@Test
	public void getDataObjectTest_deleted() {
		DataObject dataObject = new DataObject(5L);
		dataObject.setDeleted(true);
		when(dao.find(5L)).thenReturn(dataObject);
		assertNull(service.getDataObject(5L));
	}

	@Test
	public void getDataObjectTest_isNull() {
		when(dao.find(5L)).thenReturn(null);
		assertNull(service.getDataObject(5L));
	}

	@Test
	public void getDataObjectTest_deletedParent() {
		DataObject parent = new DataObject(1L);
		parent.setDeleted(true);
		DataObject dataObject = new DataObject(2L);
		dataObject.setParent(parent);
		DataObject dataObjectCut = new DataObject(2L);

		when(dao.find(2L)).thenReturn(dataObject);
		DataObject returned = service.getDataObject(2L);
		assertEquals(dataObjectCut, returned);
	}

	@Test
	public void getDataObjectTest_deletedEntities() {
		DataObject dataObjectNotDeleted = new DataObject(1L);
		DataObject dataObjectDeleted = new DataObject(2L);
		dataObjectDeleted.setDeleted(true);

		DataObjectReference doRefNotDeleted = new DataObjectReference(6L);
		DataObjectReference doRefDeleted = new DataObjectReference(7L);
		doRefDeleted.setDeleted(true);

		BasicReference refNotDeleted = new BasicReference(3L);
		BasicReference refDeleted = new BasicReference(4L);
		refDeleted.setDeleted(true);

		DataObject dataObject = new DataObject(5L);
		dataObject.setChildren(List.of(dataObjectDeleted, dataObjectNotDeleted));
		dataObject.setPredecessors(List.of(dataObjectDeleted, dataObjectNotDeleted));
		dataObject.setSuccessors(List.of(dataObjectDeleted, dataObjectNotDeleted));
		dataObject.setReferences(List.of(refDeleted, refNotDeleted));
		dataObject.setIncoming(List.of(doRefDeleted, doRefNotDeleted));

		DataObject dataObjectCut = new DataObject(5L);
		dataObjectCut.setChildren(List.of(dataObjectNotDeleted));
		dataObjectCut.setPredecessors(List.of(dataObjectNotDeleted));
		dataObjectCut.setSuccessors(List.of(dataObjectNotDeleted));
		dataObjectCut.setReferences(List.of(refNotDeleted));
		dataObjectCut.setIncoming(List.of(doRefNotDeleted));

		when(dao.find(5L)).thenReturn(dataObject);
		DataObject returned = service.getDataObject(5L);
		assertEquals(dataObjectCut, returned);
	}

	@Test
	public void getDataObjectTest_withParent() {
		DataObject parent = new DataObject(1L);
		DataObject dataObject = new DataObject(2L);
		dataObject.setParent(parent);

		when(dao.find(2L)).thenReturn(dataObject);
		DataObject returned = service.getDataObject(2L);
		assertEquals(dataObject, returned);
	}

	@Test
	public void getDataObjectsTest() {
		DataObject dataObjectNotDeleted = new DataObject(5L);

		var params = new QueryParamHelper().withName("Name");
		when(dao.findByCollection(1L, params)).thenReturn(List.of(dataObjectNotDeleted));
		List<DataObject> returned = service.getAllDataObjects(1L, params);
		assertEquals(List.of(dataObjectNotDeleted), returned);
	}

	@Test
	public void createDataObjectTest() throws InvalidBodyException {
		var user = new User("bob");
		var date = new Date(23);
		var collection = new Collection(2L);
		var parent = new DataObject(3L);
		var predecessor = new DataObject(4L);

		var input = new DataObjectIO() {
			{
				setAttributes(Map.of("a", "b", "c", "d"));
				setDescription("Desc");
				setName("Name");
				setParentId(3L);
				setPredecessorIds(new long[] { 4L });
			}
		};
		var toCreate = new DataObject() {
			{
				setAttributes(Map.of("a", "b", "c", "d"));
				setDescription("Desc");
				setName("Name");
				setCreatedAt(date);
				setCreatedBy(user);
				setCollection(collection);
				setParent(parent);
				setPredecessors(List.of(predecessor));
			}
		};
		var created = new DataObject() {
			{
				setAttributes(Map.of("a", "b", "c", "d"));
				setDescription("Desc");
				setName("Name");
				setCreatedAt(date);
				setCreatedBy(user);
				setCollection(collection);
				setParent(parent);
				setPredecessors(List.of(predecessor));
				setId(1L);
			}
		};

		when(dao.find(3L)).thenReturn(parent);
		when(dao.find(4L)).thenReturn(predecessor);
		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(collectionDAO.find(2L)).thenReturn(collection);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);

		var actual = service.createDataObject(2L, input, "bob");
		assertEquals(created, actual);
	}

	@Test
	public void createDataObjectTest_noEntites() throws InvalidBodyException {
		var user = new User("bob");
		var date = new Date(23);
		var collection = new Collection(2L);

		var input = new DataObjectIO() {
			{
				setAttributes(Map.of("a", "b", "c", "d"));
				setDescription("Desc");
				setName("Name");
			}
		};
		var toCreate = new DataObject() {
			{
				setAttributes(Map.of("a", "b", "c", "d"));
				setDescription("Desc");
				setName("Name");
				setCreatedAt(date);
				setCreatedBy(user);
				setCollection(collection);
			}
		};
		var created = new DataObject() {
			{
				setAttributes(Map.of("a", "b", "c", "d"));
				setDescription("Desc");
				setName("Name");
				setCreatedAt(date);
				setCreatedBy(user);
				setCollection(collection);
				setId(1L);
			}
		};

		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(collectionDAO.find(2L)).thenReturn(collection);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);

		var actual = service.createDataObject(2L, input, "bob");
		assertEquals(created, actual);
	}

	@Test
	public void createDataObjectTest_wrongParent() throws InvalidBodyException {
		var user = new User("bob");
		var date = new Date(23);
		var collection = new Collection(2L);

		var input = new DataObjectIO() {
			{
				setAttributes(Map.of("a", "b", "c", "d"));
				setDescription("Desc");
				setName("Name");
				setParentId(3L);
			}
		};

		when(dao.find(3L)).thenReturn(null);
		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(collectionDAO.find(2L)).thenReturn(collection);

		assertThrows(InvalidBodyException.class, () -> service.createDataObject(2L, input, "bob"));
	}

	@Test
	public void createDataObjectTest_wrongPredecessor() throws InvalidBodyException {
		var user = new User("bob");
		var date = new Date(23);
		var collection = new Collection(2L);

		var input = new DataObjectIO() {
			{
				setAttributes(Map.of("a", "b", "c", "d"));
				setDescription("Desc");
				setName("Name");
				setPredecessorIds(new long[] { 3L });
			}
		};

		when(dao.find(3L)).thenReturn(null);
		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(collectionDAO.find(2L)).thenReturn(collection);

		assertThrows(InvalidBodyException.class, () -> service.createDataObject(2L, input, "bob"));
	}

	@Test
	public void createDataObjectTest_deletedParent() throws InvalidBodyException {
		var user = new User("bob");
		var date = new Date(23);
		var collection = new Collection(2L);
		var parent = new DataObject(3L);
		parent.setDeleted(true);

		var input = new DataObjectIO() {
			{
				setAttributes(Map.of("a", "b", "c", "d"));
				setDescription("Desc");
				setName("Name");
				setParentId(3L);
			}
		};

		when(dao.find(3L)).thenReturn(parent);
		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(collectionDAO.find(2L)).thenReturn(collection);

		assertThrows(InvalidBodyException.class, () -> service.createDataObject(2L, input, "bob"));
	}

	@Test
	public void createDataObjectTest_deletedPredecessor() throws InvalidBodyException {
		var user = new User("bob");
		var date = new Date(23);
		var collection = new Collection(2L);
		var predecessor = new DataObject(3L);
		predecessor.setDeleted(true);

		var input = new DataObjectIO() {
			{
				setAttributes(Map.of("a", "b", "c", "d"));
				setDescription("Desc");
				setName("Name");
				setPredecessorIds(new long[] { 3L });
			}
		};

		when(dao.find(3L)).thenReturn(predecessor);
		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(collectionDAO.find(2L)).thenReturn(collection);

		assertThrows(InvalidBodyException.class, () -> service.createDataObject(2L, input, "bob"));
	}

	@Test
	public void updateDataObjectTest() throws InvalidBodyException {
		var user = new User("bob");
		var date = new Date(23);
		var updateUser = new User("claus");
		var updateDate = new Date(43);
		var parent = new DataObject(3L);
		var predecessor = new DataObject(4L);

		var input = new DataObjectIO() {
			{
				setId(1L);
				setAttributes(Map.of("1", "2", "c", "d"));
				setDescription("newDesc");
				setName("newName");
				setParentId(3L);
				setPredecessorIds(new long[] { 4L });
			}
		};
		var old = new DataObject() {
			{
				setAttributes(Map.of("a", "b", "c", "d"));
				setDescription("Desc");
				setName("Name");
				setCreatedAt(date);
				setCreatedBy(user);
				setId(1L);
			}
		};
		var updated = new DataObject() {
			{
				setAttributes(Map.of("1", "2", "c", "d"));
				setDescription("newDesc");
				setName("newName");
				setCreatedAt(date);
				setCreatedBy(user);
				setUpdatedAt(updateDate);
				setUpdatedBy(updateUser);
				setParent(parent);
				setPredecessors(List.of(predecessor));
				setId(1L);
			}
		};

		when(dao.find(1L)).thenReturn(old);
		when(dao.find(3L)).thenReturn(parent);
		when(dao.find(4L)).thenReturn(predecessor);
		when(userDAO.find("claus")).thenReturn(updateUser);
		when(dateHelper.getDate()).thenReturn(updateDate);
		when(dao.createOrUpdate(updated)).thenReturn(updated);

		var actual = service.updateDataObject(1L, input, "claus");
		assertEquals(updated, actual);
	}

	@Test
	public void deleteDataObjectTest() {
		var user = new User("bob");
		var date = new Date(23);

		var dataObject = new DataObject(1L);

		when(userDAO.find("bob")).thenReturn(user);
		when(dateHelper.getDate()).thenReturn(date);
		when(dao.find(1L)).thenReturn(dataObject);
		when(dao.deleteDataObject(1L, user, date)).thenReturn(true);

		var result = service.deleteDataObject(1L, "bob");
		assertTrue(result);
	}
}
