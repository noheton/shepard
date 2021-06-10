package de.dlr.shepard.mongoDB;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.util.UUIDHelper;

public class FileServiceTest extends BaseTestCase {

	@Mock
	private MongoDBConnector mongoDBConnector;
	@Mock
	private UUIDHelper uuidhelper;
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
	public void getFileTest() {
		String containerId = "FileContainerdc824045-9137-4051-8981-c528e6b91fbe";
		String nonExistingContainerId = "FileContainer123";
		String fileoid = "60b73212cfa45d2d5baa795d";
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
		Mockito.when(mongoDatabase.getCollection(nonExistingContainerId)).thenReturn(null);
		Mockito.when(collection.find(eq("_id", new ObjectId(fileoid)))).thenReturn(collectionReturn);
		Mockito.when(collection.find(eq("_id", new ObjectId(nonExistingFileoid)))).thenReturn(emptyCollectionReturn);
		Mockito.when(collectionReturn.first()).thenReturn(file);
		Mockito.when(emptyCollectionReturn.first()).thenReturn(null);
		Mockito.when(file.getString("name")).thenReturn("name");
		var result = fileService.getFile(containerId, fileoid);
		System.out.println("result: " + result);
		assertEquals(result.getFilename(), name);
		assertEquals(result.getOid(), fileoid);
		result = fileService.getFile(nonExistingContainerId, fileoid);
		System.out.println("result: " + result);
		assertEquals(result, null);
		result = fileService.getFile(containerId, nonExistingFileoid);
		System.out.println("result: " + result);
		assertEquals(result, null);
	}
}
