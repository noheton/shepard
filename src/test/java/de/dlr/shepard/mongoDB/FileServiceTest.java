package de.dlr.shepard.mongoDB;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;

import java.io.InputStream;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.util.UUIDHelper;

public class FileServiceTest extends BaseTestCase {

	@Mock
	private MongoDBConnector mongoDBConnector;
	@Mock
	private UUIDHelper uuidhelper;
	@Mock
	private MongoCollection<Document> collection;
	@Mock
	private MongoDatabase database;
	@Mock
	private GridFSBucket gridBucket;
	@InjectMocks
	private FileService fileService;

	@Test
	public void createFileContainerTest() {
		var uuid = UUID.randomUUID();
		Mockito.when(uuidhelper.getUUID()).thenReturn(uuid);
		var result = fileService.createFileContainer();
		var expectedUUID = "FileContainer" + uuid.toString();
		assertEquals(expectedUUID, result);
		Mockito.verify(mongoDBConnector).createCollection(expectedUUID);
	}

	@Test
	public void getExistingFileTest() {
		String containerId = "FileContainerdc824045-9137-4051-8981-c528e6b91fbe";
		String fileoid = "60b73212cfa45d2d5baa795d";
		String name = "name";
		@SuppressWarnings("unchecked")
		MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
		MongoDatabase mongoDatabase = Mockito.mock(MongoDatabase.class);
		@SuppressWarnings("unchecked")
		FindIterable<Document> collectionReturn = Mockito.mock(FindIterable.class);
		@SuppressWarnings("unchecked")
		FindIterable<Document> emptyCollectionReturn = Mockito.mock(FindIterable.class);
		Document file = Mockito.mock(Document.class);
		Mockito.when(mongoDBConnector.getDatabase()).thenReturn(mongoDatabase);
		Mockito.when(mongoDatabase.getCollection(containerId)).thenReturn(collection);
		Mockito.when(collection.find(eq("_id", new ObjectId(fileoid)))).thenReturn(collectionReturn);
		Mockito.when(collectionReturn.first()).thenReturn(file);
		Mockito.when(emptyCollectionReturn.first()).thenReturn(null);
		Mockito.when(file.getString("name")).thenReturn("name");
		var result = fileService.getFile(containerId, fileoid);
		System.out.println("result: " + result);
		assertEquals(result.getFilename(), name);
		assertEquals(result.getOid(), fileoid);
	}

	@Test
	public void getNonExistingContainerFileTest() {
		String nonExistingContainerId = "FileContainer123";
		String fileoid = "60b73212cfa45d2d5baa795d";
		@SuppressWarnings("unchecked")
		MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
		MongoDatabase mongoDatabase = Mockito.mock(MongoDatabase.class);
		@SuppressWarnings("unchecked")
		FindIterable<Document> collectionReturn = Mockito.mock(FindIterable.class);
		@SuppressWarnings("unchecked")
		FindIterable<Document> emptyCollectionReturn = Mockito.mock(FindIterable.class);
		Document file = Mockito.mock(Document.class);
		Mockito.when(mongoDBConnector.getDatabase()).thenReturn(mongoDatabase);
		Mockito.when(mongoDatabase.getCollection(nonExistingContainerId)).thenReturn(null);
		Mockito.when(collection.find(eq("_id", new ObjectId(fileoid)))).thenReturn(collectionReturn);
		Mockito.when(collectionReturn.first()).thenReturn(file);
		Mockito.when(emptyCollectionReturn.first()).thenReturn(null);
		Mockito.when(file.getString("name")).thenReturn("name");
		var result = fileService.getFile(nonExistingContainerId, fileoid);
		System.out.println("result: " + result);
		assertEquals(result, null);
	}

	@Test
	public void getNonExistingFileTest() {
		String containerId = "FileContainerdc824045-9137-4051-8981-c528e6b91fbe";
		String nonExistingFileoid = "60b73212cfa45d2d5baa795b";
		String name = "name";
		@SuppressWarnings("unchecked")
		MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
		MongoDatabase mongoDatabase = Mockito.mock(MongoDatabase.class);
		@SuppressWarnings("unchecked")
		FindIterable<Document> collectionReturn = Mockito.mock(FindIterable.class);
		@SuppressWarnings("unchecked")
		FindIterable<Document> emptyCollectionReturn = Mockito.mock(FindIterable.class);
		Document file = Mockito.mock(Document.class);
		Mockito.when(mongoDBConnector.getDatabase()).thenReturn(mongoDatabase);
		Mockito.when(mongoDatabase.getCollection(containerId)).thenReturn(collection);
		Mockito.when(collection.find(eq("_id", new ObjectId(nonExistingFileoid)))).thenReturn(emptyCollectionReturn);
		Mockito.when(collectionReturn.first()).thenReturn(file);
		Mockito.when(emptyCollectionReturn.first()).thenReturn(null);
		Mockito.when(file.getString("name")).thenReturn("name");
		var result = fileService.getFile(containerId, nonExistingFileoid);
		System.out.println("result: " + result);
		assertEquals(result, null);
	}

	@Test
	public void createFileTest() {
		ObjectId oid = new ObjectId();
		ObjectId moid = new ObjectId();
		String fileName = "fileName";
		InputStream inputStream = Mockito.mock(InputStream.class);
		String mongoid = "mongoid";
		Mockito.when(mongoDBConnector.getDatabase()).thenReturn(database);
		Mockito.when(database.getCollection(mongoid)).thenReturn(collection);
		Mockito.when(mongoDBConnector.createBucket()).thenReturn(gridBucket);
		Mockito.when(gridBucket.uploadFromStream(Mockito.eq(fileName), Mockito.eq(inputStream),
				Mockito.any(GridFSUploadOptions.class))).thenReturn(oid);
		Mockito.when(collection.insertOne(Mockito.any())).thenReturn(null);
		doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				Object[] args = invocation.getArguments();
				((Document) args[0]).append("_id", moid);
				return null;
			}
		}).when(collection).insertOne(Mockito.any(Document.class));
		var result = fileService.createFile(mongoid, fileName, inputStream);
		assertEquals(result.getOid(), moid.toHexString());
		assertEquals(result.getFilename(), fileName);
	}

	@Test
	public void createNonExistingMonoidFileTest() {
		String fileName = "fileName";
		InputStream inputStream = Mockito.mock(InputStream.class);
		String nonExistingMongoid = "mongoid";
		Mockito.when(mongoDBConnector.getDatabase()).thenReturn(database);
		Mockito.when(database.getCollection(nonExistingMongoid)).thenReturn(null);
		var result = fileService.createFile(nonExistingMongoid, fileName, inputStream);
		assertEquals(result, null);
	}

	@Test
	public void deleteExistingFileContainerTest() {
		String existingMongoOid = "60b73212cfa45d2d5baa795d";
		@SuppressWarnings("unchecked")
		MongoCollection<Document> toDelete = Mockito.mock(MongoCollection.class);
		MongoDatabase mongoDatabase = Mockito.mock(MongoDatabase.class);
		Mockito.when(mongoDBConnector.getDatabase()).thenReturn(mongoDatabase);
		Mockito.when(mongoDBConnector.createBucket()).thenReturn(gridBucket);
		Mockito.when(mongoDatabase.getCollection(existingMongoOid)).thenReturn(toDelete);
		@SuppressWarnings("unchecked")
		FindIterable<Document> emptyCollectionReturn = Mockito.mock(FindIterable.class);
		Mockito.when(toDelete.find()).thenReturn(emptyCollectionReturn);
		// TODO: Iterator mocken
		// var result = fileService.deleteFileContainer(existingMongoOid);
	}

	@Test
	public void deleteNonExistingFileContainerTest() {
		String nonExistingMongoOid = "60b73212cfa45d2d5baa795d";
		MongoDatabase mongoDatabase = Mockito.mock(MongoDatabase.class);
		Mockito.when(mongoDBConnector.getDatabase()).thenReturn(mongoDatabase);
		Mockito.when(mongoDBConnector.createBucket()).thenReturn(gridBucket);
		Mockito.when(mongoDatabase.getCollection(nonExistingMongoOid)).thenReturn(null);
		var result = fileService.deleteFileContainer(nonExistingMongoOid);
		assertEquals(result, false);
	}

	@Test
	public void getPayloadNonExistingContainerIdTest() {
		String nonExistingContainerId = "4";
		String fileoid = "60b73212cfa45d2d5baa795d";
		MongoDatabase mongoDatabase = Mockito.mock(MongoDatabase.class);
		Mockito.when(mongoDBConnector.getDatabase()).thenReturn(mongoDatabase);
		Mockito.when(mongoDatabase.getCollection(nonExistingContainerId)).thenReturn(null);
		var result = fileService.getPayload(nonExistingContainerId, fileoid);
		assertEquals(result, null);
	}

	@Test
	public void getPayloadNonExistingFileOidTest() {
		String nonExistingContainerId = "4";
		String fileoid = "60b73212cfa45d2d5baa795d";
		MongoDatabase mongoDatabase = Mockito.mock(MongoDatabase.class);
		Mockito.when(mongoDBConnector.getDatabase()).thenReturn(mongoDatabase);
		@SuppressWarnings("unchecked")
		MongoCollection<Document> toDelete = Mockito.mock(MongoCollection.class);
		Mockito.when(mongoDatabase.getCollection(nonExistingContainerId)).thenReturn(toDelete);
		@SuppressWarnings("unchecked")
		FindIterable<Document> emptyCollectionReturn = Mockito.mock(FindIterable.class);
		Mockito.when(toDelete.find(eq("_id", new ObjectId(fileoid)))).thenReturn(emptyCollectionReturn);
		Mockito.when(emptyCollectionReturn.first()).thenReturn(null);
		var result = fileService.getPayload(nonExistingContainerId, fileoid);
		assertEquals(result, null);
	}
}
