package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.BasicReferenceDAO;
import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.DataObjectReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.QueryParamHelper;

public class CollectionServiceTest extends BaseTestCase {

	@Mock
	private CollectionDAO dao;

	@Mock
	private DataObjectDAO dataObjectDAO;

	@Mock
	private BasicReferenceDAO referenceDAO;

	@Mock
	private UserDAO userDAO;

	@Mock
	private DateHelper dateHelper;

	@InjectMocks
	private CollectionService service;

	@Test
	public void getCollectionTest() {
		Collection collection = new Collection(1L);
		when(dao.find(1L)).thenReturn(collection);
		assertEquals(collection, service.getCollection(1L));
	}

	@Test
	public void getCollectionTest_deleted() {
		Collection collection = new Collection(1L);
		collection.setDeleted(true);
		when(dao.find(1L)).thenReturn(collection);
		assertNull(service.getCollection(1L));
	}

	@Test
	public void getCollectionTest_isNull() {
		when(dao.find(10L)).thenReturn(null);
		assertNull(service.getCollection(10L));
	}

	@Test
	public void getCollectionTest_deletedEntites() {
		DataObject doNotDeleted = new DataObject(1L);
		DataObject doDeleted = new DataObject(2L);
		doDeleted.setDeleted(true);
		DataObjectReference refNotDeleted = new DataObjectReference(3L);
		DataObjectReference refDeleted = new DataObjectReference(4L);
		refDeleted.setDeleted(true);

		Collection collection = new Collection(5L);
		collection.setDataObjects(List.of(doDeleted, doNotDeleted));
		collection.setIncoming(List.of(refDeleted, refNotDeleted));

		Collection collectionCut = new Collection(5L);
		collectionCut.setDataObjects(List.of(doNotDeleted));
		collectionCut.setIncoming(List.of(refNotDeleted));

		when(dao.find(5L)).thenReturn(collection);
		Collection returned = service.getCollection(5L);
		assertEquals(collectionCut, returned);
	}

	@Test
	public void getCollectionsTest() {
		Collection collectionNotDeleted = new Collection(5L);
		Collection collectionDeleted = new Collection(6L);
		collectionDeleted.setDeleted(true);

		when(dao.findAllCollections(null)).thenReturn(List.of(collectionDeleted, collectionNotDeleted));
		List<Collection> returned = service.getAllCollections(null);
		assertEquals(List.of(collectionNotDeleted), returned);
	}

	@Test
	public void getCollectionsTest_withName() {
		Collection collectionNotDeleted = new Collection(5L);
		Collection collectionDeleted = new Collection(6L);
		collectionDeleted.setDeleted(true);

		var params = new QueryParamHelper().withName("test");
		when(dao.findAllCollections(params)).thenReturn(List.of(collectionDeleted, collectionNotDeleted));
		List<Collection> returned = service.getAllCollections(params);
		assertEquals(List.of(collectionNotDeleted), returned);
	}

	@Test
	public void createCollectionTest() {
		var user = new User("bob");
		var date = new Date(23);

		var input = new CollectionIO() {
			{
				setAttributes(Map.of("a", "b", "c", "d"));
				setDescription("Desc");
				setName("Name");
			}
		};
		var toCreate = new Collection() {
			{
				setAttributes(Map.of("a", "b", "c", "d"));
				setDescription("Desc");
				setName("Name");
				setCreatedAt(date);
				setCreatedBy(user);
			}
		};
		var created = new Collection() {
			{
				setAttributes(Map.of("a", "b", "c", "d"));
				setDescription("Desc");
				setName("Name");
				setCreatedAt(date);
				setCreatedBy(user);
				setId(1L);
			}
		};

		when(userDAO.find("bob")).thenReturn(user);
		when(dateHelper.getDate()).thenReturn(date);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);

		var actual = service.createCollection(input, "bob");
		assertEquals(created, actual);
	}

	@Test
	public void updateCollectionTest() {
		var user = new User("bob");
		var date = new Date(23);
		var updateUser = new User("claus");
		var updateDate = new Date(43);

		var input = new CollectionIO() {
			{
				setId(1L);
				setAttributes(Map.of("1", "2", "c", "d"));
				setDescription("newDesc");
				setName("newName");
			}
		};
		var old = new Collection() {
			{
				setAttributes(Map.of("a", "b", "c", "d"));
				setDescription("Desc");
				setName("Name");
				setCreatedAt(date);
				setCreatedBy(user);
				setId(1L);
			}
		};
		var updated = new Collection() {
			{
				setAttributes(Map.of("1", "2", "c", "d"));
				setDescription("newDesc");
				setName("newName");
				setCreatedAt(date);
				setCreatedBy(user);
				setUpdatedAt(updateDate);
				setUpdatedBy(updateUser);
				setId(1L);
			}
		};

		when(dao.find(1L)).thenReturn(old);
		when(userDAO.find("claus")).thenReturn(updateUser);
		when(dateHelper.getDate()).thenReturn(updateDate);
		when(dao.createOrUpdate(updated)).thenReturn(updated);

		var actual = service.updateCollection(1L, input, "claus");
		assertEquals(updated, actual);
	}

	@Test
	public void deleteCollectionTest() {
		var user = new User("bob");
		var date = new Date(23);

		var reference = new BasicReference(3L);
		var dataObject = new DataObject(2L);
		dataObject.setReferences(List.of(reference));
		var collection = new Collection(1L);
		collection.setDataObjects(List.of(dataObject));

		var referenceDeleted = new BasicReference(3L);
		referenceDeleted.setUpdatedAt(date);
		referenceDeleted.setUpdatedBy(user);
		referenceDeleted.setDeleted(true);
		var dataObjectDeleted = new DataObject(2L);
		dataObjectDeleted.setReferences(List.of(referenceDeleted));
		dataObjectDeleted.setUpdatedAt(date);
		dataObjectDeleted.setUpdatedBy(user);
		dataObjectDeleted.setDeleted(true);
		var collectionDeleted = new Collection(1L);
		collectionDeleted.setDataObjects(List.of(dataObjectDeleted));
		collectionDeleted.setUpdatedAt(date);
		collectionDeleted.setUpdatedBy(user);
		collectionDeleted.setDeleted(true);

		when(userDAO.find("bob")).thenReturn(user);
		when(dateHelper.getDate()).thenReturn(date);
		when(dao.find(1L)).thenReturn(collection);
		when(dataObjectDAO.find(2L)).thenReturn(dataObject);
		when(referenceDAO.find(3L)).thenReturn(reference);

		service.deleteCollection(1L, "bob");
		verify(referenceDAO).createOrUpdate(referenceDeleted);
		verify(dataObjectDAO).createOrUpdate(dataObjectDeleted);
		verify(dao).createOrUpdate(collectionDeleted);
	}
}
