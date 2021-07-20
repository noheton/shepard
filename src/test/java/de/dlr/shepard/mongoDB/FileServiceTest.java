package de.dlr.shepard.mongoDB;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.mongodb.client.model.Filters;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.UUIDHelper;

public class FileServiceTest extends BaseTestCase {

	@Mock
	private MongoDBConnector mongoDBConnector;
	@Mock
	private DateHelper dateHelper;
	@Mock
	private UUIDHelper uuidhelper;
	@InjectMocks
	private FileService fileService;

	@Mock
	private MongoCollection<Document> collection;
	@Mock
	private MongoDatabase database;
	@Mock
	private GridFSBucket gridBucket;

	@BeforeEach
	public void setUpConnector() {
		when(mongoDBConnector.getDatabase()).thenReturn(database);
		when(mongoDBConnector.createBucket()).thenReturn(gridBucket);
	}

	@Test
	public void createFileContainerTest() {
		var uuid = UUID.randomUUID();
		when(uuidhelper.getUUID()).thenReturn(uuid);
		var result = fileService.createFileContainer();
		var expectedUUID = "FileContainer" + uuid.toString();
		assertEquals(expectedUUID, result);
		verify(mongoDBConnector).createCollection(expectedUUID);
	}

	@Test
	public void getExistingFileTest() {
		String containerId = "FileContainerdc824045-9137-4051-8981-c528e6b91fbe";
		String fileoid = "60b73212cfa45d2d5baa795d";
		String name = "name";
		Date date = new Date();
		Document file = mock(Document.class);
		@SuppressWarnings("unchecked")
		FindIterable<Document> collectionReturn = mock(FindIterable.class);

		when(database.getCollection(containerId)).thenReturn(collection);
		when(collection.find(Filters.eq("_id", new ObjectId(fileoid)))).thenReturn(collectionReturn);
		when(collectionReturn.first()).thenReturn(file);
		when(file.getString("name")).thenReturn("name");
		when(file.getDate("createdAt")).thenReturn(date);

		var expected = new File(fileoid, date, name);
		var result = fileService.getFile(containerId, fileoid);
		assertEquals(expected, result);
	}

	@Test
	public void getNonExistingContainerFileTest() {
		String nonExistingContainerId = "FileContainer123";
		String fileoid = "60b73212cfa45d2d5baa795d";

		when(database.getCollection(nonExistingContainerId)).thenReturn(null);
		var result = fileService.getFile(nonExistingContainerId, fileoid);
		assertNull(result);
	}

	@Test
	public void getNonExistingFileTest() {
		String containerId = "FileContainerdc824045-9137-4051-8981-c528e6b91fbe";
		String nonExistingFileoid = "60b73212cfa45d2d5baa795b";
		@SuppressWarnings("unchecked")
		FindIterable<Document> collectionReturn = mock(FindIterable.class);

		when(database.getCollection(containerId)).thenReturn(collection);
		when(collection.find(Filters.eq("_id", new ObjectId(nonExistingFileoid)))).thenReturn(collectionReturn);
		when(collectionReturn.first()).thenReturn(null);
		var result = fileService.getFile(containerId, nonExistingFileoid);
		assertEquals(result, null);
	}

	@Test
	public void createFileTest() {
		ObjectId oid = new ObjectId();
		ObjectId moid = new ObjectId();
		String fileName = "fileName";
		InputStream inputStream = mock(InputStream.class);
		String mongoid = "mongoid";
		Date date = new Date();

		when(dateHelper.getDate()).thenReturn(date);
		when(database.getCollection(mongoid)).thenReturn(collection);
		when(gridBucket.uploadFromStream(eq(fileName), eq(inputStream), any(GridFSUploadOptions.class)))
				.thenReturn(oid);
		doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				Object[] args = invocation.getArguments();
				((Document) args[0]).append("_id", moid);
				return null;
			}
		}).when(collection).insertOne(any(Document.class));

		var newFile = new Document("_id", moid).append("name", fileName).append("container", mongoid)
				.append("FileMongoId", oid.toHexString()).append("createdAt", date);
		var expected = new File(moid.toHexString(), date, fileName);
		var result = fileService.createFile(mongoid, fileName, inputStream);
		assertEquals(expected, result);
		verify(collection).insertOne(newFile);
	}

	@Test
	public void createNonExistingMongoidFileTest() {
		String fileName = "fileName";
		InputStream inputStream = mock(InputStream.class);
		String nonExistingMongoid = "mongoid";

		when(database.getCollection(nonExistingMongoid)).thenReturn(null);
		var result = fileService.createFile(nonExistingMongoid, fileName, inputStream);
		assertNull(result);
	}

	@Test
	public void deleteExistingFileContainerTest() {
		String existingMongoOid = "60b73212cfa45d2d5baa795d";
		ObjectId oid = new ObjectId();
		@SuppressWarnings("unchecked")
		FindIterable<Document> collectionReturn = mock(FindIterable.class);
		Document doc = mock(Document.class);
		mockIterable(collectionReturn, doc);

		when(database.getCollection(existingMongoOid)).thenReturn(collection);
		when(collection.find()).thenReturn(collectionReturn);
		when(doc.getString("FileMongoId")).thenReturn(oid.toString());

		var result = fileService.deleteFileContainer(existingMongoOid);
		assertTrue(result);
		verify(gridBucket).delete(oid);
	}

	@Test
	public void deleteNonExistingFileContainerTest() {
		String nonExistingMongoOid = "60b73212cfa45d2d5baa795d";

		when(database.getCollection(nonExistingMongoOid)).thenReturn(null);
		var result = fileService.deleteFileContainer(nonExistingMongoOid);
		assertFalse(result);
	}

	@Test
	public void getPayloadTest() {
		String containerId = "4";
		String fileoid = "60b73212cfa45d2d5baa795d";
		String fileName = "FileName";
		@SuppressWarnings("unchecked")
		FindIterable<Document> emptyCollectionReturn = mock(FindIterable.class);
		Document doc = mock(Document.class);
		ObjectId oid = new ObjectId();
		GridFSDownloadStream stream = mock(GridFSDownloadStream.class);

		when(database.getCollection(containerId)).thenReturn(collection);
		when(collection.find(Filters.eq("_id", new ObjectId(fileoid)))).thenReturn(emptyCollectionReturn);
		when(emptyCollectionReturn.first()).thenReturn(doc);
		when(doc.getString("FileMongoId")).thenReturn(oid.toString());
		when(doc.getString("name")).thenReturn(fileName);
		when(gridBucket.openDownloadStream(oid)).thenReturn(stream);

		var expected = new NamedInputStream(stream, fileName);
		var result = fileService.getPayload(containerId, fileoid);
		assertEquals(expected, result);
	}

	@Test
	public void getPayloadNonExistingContainerIdTest() {
		String nonExistingContainerId = "4";
		String fileoid = "60b73212cfa45d2d5baa795d";

		when(database.getCollection(nonExistingContainerId)).thenReturn(null);
		var result = fileService.getPayload(nonExistingContainerId, fileoid);
		assertNull(result);
	}

	@Test
	public void getPayloadNonExistingFileOidTest() {
		String containerId = "4";
		String fileoid = "60b73212cfa45d2d5baa795d";

		when(database.getCollection(containerId)).thenReturn(collection);
		@SuppressWarnings("unchecked")
		FindIterable<Document> emptyCollectionReturn = mock(FindIterable.class);
		when(collection.find(Filters.eq("_id", new ObjectId(fileoid)))).thenReturn(emptyCollectionReturn);
		when(emptyCollectionReturn.first()).thenReturn(null);

		var result = fileService.getPayload(containerId, fileoid);
		assertNull(result);
	}

	@Test
	public void deletePayloadTest() {
		String fileOid = "60b73212cfa45d2d5baa795a";
		String mongoOid = "60b73212cfa45d2d5baa795b";
		Document doc = mock(Document.class);
		ObjectId oid = new ObjectId("60b73212cfa45d2d5baa795c");

		when(database.getCollection(mongoOid)).thenReturn(collection);
		when(collection.findOneAndDelete(Filters.eq("_id", oid))).thenReturn(doc);
		when(doc.getString("FileMongoId")).thenReturn(fileOid);

		var result = fileService.deleteFile(mongoOid, oid.toString());
		assertTrue(result);
		verify(gridBucket).delete(new ObjectId(fileOid));
	}

	@Test
	public void deletePayloadTest_collectionIsNull() {
		String mongoOid = "60b73212cfa45d2d5baa795b";
		ObjectId oid = new ObjectId("60b73212cfa45d2d5baa795c");

		when(database.getCollection(mongoOid)).thenReturn(null);

		var result = fileService.deleteFile(mongoOid, oid.toString());
		assertFalse(result);
	}

	@Test
	public void deletePayloadTest_documentIsNull() {
		String mongoOid = "60b73212cfa45d2d5baa795b";
		ObjectId oid = new ObjectId("60b73212cfa45d2d5baa795c");

		when(database.getCollection(mongoOid)).thenReturn(collection);
		when(collection.findOneAndDelete(Filters.eq("_id", oid))).thenReturn(null);

		var result = fileService.deleteFile(mongoOid, oid.toString());
		assertFalse(result);
	}

	/**
	 * From https://www.batey.info/mocking-iterable-objects-generically.html
	 *
	 * @param iterable
	 * @param values
	 */
	@SuppressWarnings("unchecked")
	private static void mockIterable(FindIterable<Document> iterable, Document... values) {
		MongoCursor<Document> mockIterator = mock(MongoCursor.class);
		when(iterable.iterator()).thenReturn(mockIterator);

		if (values.length == 0) {
			when(mockIterator.hasNext()).thenReturn(false);
			return;
		} else if (values.length == 1) {
			when(mockIterator.hasNext()).thenReturn(true, false);
			when(mockIterator.next()).thenReturn(values[0]);
		} else {
			// build boolean array for hasNext()
			Boolean[] hasNextResponses = new Boolean[values.length];
			for (int i = 0; i < hasNextResponses.length - 1; i++) {
				hasNextResponses[i] = true;
			}
			hasNextResponses[hasNextResponses.length - 1] = false;
			when(mockIterator.hasNext()).thenReturn(true, hasNextResponses);
			Document[] valuesMinusTheFirst = Arrays.copyOfRange(values, 1, values.length);
			when(mockIterator.next()).thenReturn(values[0], valuesMinusTheFirst);
		}
	}
}
