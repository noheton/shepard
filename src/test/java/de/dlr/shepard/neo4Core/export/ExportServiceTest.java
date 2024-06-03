package de.dlr.shepard.neo4Core.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.FileReference;
import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import de.dlr.shepard.neo4Core.entities.TimeseriesReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.BasicReferenceIO;
import de.dlr.shepard.neo4Core.io.FileReferenceIO;
import de.dlr.shepard.neo4Core.io.StructuredDataReferenceIO;
import de.dlr.shepard.neo4Core.io.TimeseriesReferenceIO;
import de.dlr.shepard.neo4Core.services.BasicReferenceService;
import de.dlr.shepard.neo4Core.services.CollectionService;
import de.dlr.shepard.neo4Core.services.DataObjectService;
import de.dlr.shepard.neo4Core.services.FileReferenceService;
import de.dlr.shepard.neo4Core.services.StructuredDataReferenceService;
import de.dlr.shepard.neo4Core.services.TimeseriesReferenceService;
import de.dlr.shepard.neo4Core.services.UserService;
import de.dlr.shepard.util.DateHelper;

public class ExportServiceTest extends BaseTestCase {

	private DateHelper dateHelper = new DateHelper();

	@Mock
	private UserService userService;

	@Mock
	private CollectionService collectionService;

	@Mock
	private DataObjectService dataObjectService;

	@Mock
	private BasicReferenceService basicReferenceService;

	@Mock
	private TimeseriesReferenceService timeseriesReferenceService;

	@Mock
	private FileReferenceService fileReferenceService;

	@Mock
	private StructuredDataReferenceService structuredDataReferenceService;

	@InjectMocks
	private ExportService service;

	private final User user = new User("bob");
	private final DataObject dataObject = new DataObject() {
		{
			setShepardId(25L);
			setCreatedAt(dateHelper.getDate());
			setCreatedBy(user);
		}
	};
	private final Collection collection = new Collection() {
		{
			setShepardId(15L);
			setCreatedAt(dateHelper.getDate());
			setCreatedBy(user);
		}
	};

	@BeforeEach
	private void initMocks() {
		collection.addDataObject(dataObject);
		dataObject.setCollection(collection);

		when(userService.getUser(user.getUsername())).thenReturn(user);
		when(collectionService.getCollectionByShepardId(collection.getShepardId())).thenReturn(collection);
		when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
	}

	@Test
	public void exportTest_basicReference() throws IOException {
		var reference = hydrateReferenceMock(mock(BasicReference.class), "BasicReference");
		dataObject.addReference(reference);
		when(basicReferenceService.getReferenceByShepardId(reference.getShepardId())).thenReturn(reference);

		var mockStream = mock(InputStream.class);
		try (var exportBuilderMockController = mockConstruction(ExportBuilder.class, (mock, context) -> {
			when(mock.build()).thenReturn(mockStream);
		});) {

			var actual = service.exportCollectionByShepardId(collection.getShepardId(), user.getUsername());

			var exportBuilderMock = exportBuilderMockController.constructed().get(0);
			verify(exportBuilderMock).addReference(any(BasicReferenceIO.class), eq(user));
			verify(exportBuilderMock).addDataObject(dataObject);

			assertEquals(1, exportBuilderMockController.constructed().size());
			assertEquals(mockStream, actual);
		}
	}

	@Test
	public void exportTest_timeseriesReference() throws IOException {
		var reference = hydrateReferenceMock(mock(TimeseriesReference.class), "TimeseriesReference");
		dataObject.addReference(reference);
		when(timeseriesReferenceService.getReferenceByShepardId(reference.getShepardId())).thenReturn(reference);

		var mockStream = mock(InputStream.class);
		try (var exportBuilderMockController = mockConstruction(ExportBuilder.class, (mock, context) -> {
			when(mock.build()).thenReturn(mockStream);
		});) {

			var actual = service.exportCollectionByShepardId(collection.getShepardId(), user.getUsername());

			var exportBuilderMock = exportBuilderMockController.constructed().get(0);
			verify(exportBuilderMock).addReference(any(TimeseriesReferenceIO.class), eq(user));
			verify(exportBuilderMock).addDataObject(dataObject);

			assertEquals(1, exportBuilderMockController.constructed().size());
			assertEquals(mockStream, actual);
		}
	}

	@Test
	public void exportTest_fileReference() throws IOException {
		var reference = hydrateReferenceMock(mock(FileReference.class), "FileReference");
		dataObject.addReference(reference);
		when(fileReferenceService.getReferenceByShepardId(reference.getShepardId())).thenReturn(reference);

		var mockStream = mock(InputStream.class);
		try (var exportBuilderMockController = mockConstruction(ExportBuilder.class, (mock, context) -> {
			when(mock.build()).thenReturn(mockStream);
		});) {

			var actual = service.exportCollectionByShepardId(collection.getShepardId(), user.getUsername());

			var exportBuilderMock = exportBuilderMockController.constructed().get(0);
			verify(exportBuilderMock).addReference(any(FileReferenceIO.class), eq(user));
			verify(exportBuilderMock).addDataObject(dataObject);

			assertEquals(1, exportBuilderMockController.constructed().size());
			assertEquals(mockStream, actual);
		}
	}

	@Test
	public void exportTest_structuredDataReference() throws IOException {
		var reference = hydrateReferenceMock(mock(StructuredDataReference.class), "StructuredDataReference");
		dataObject.addReference(reference);
		when(structuredDataReferenceService.getReferenceByShepardId(reference.getShepardId())).thenReturn(reference);

		var mockStream = mock(InputStream.class);
		try (var exportBuilderMockController = mockConstruction(ExportBuilder.class, (mock, context) -> {
			when(mock.build()).thenReturn(mockStream);
		});) {

			var actual = service.exportCollectionByShepardId(collection.getShepardId(), user.getUsername());

			var exportBuilderMock = exportBuilderMockController.constructed().get(0);
			verify(exportBuilderMock).addReference(any(StructuredDataReferenceIO.class), eq(user));
			verify(exportBuilderMock).addDataObject(dataObject);

			assertEquals(1, exportBuilderMockController.constructed().size());
			assertEquals(mockStream, actual);
		}
	}

	private <T extends BasicReference> T hydrateReferenceMock(T reference, String type) {
		when(reference.getShepardId()).thenReturn(35L);
		when(reference.getCreatedBy()).thenReturn(user);
		when(reference.getCreatedAt()).thenReturn(dateHelper.getDate());
		when(reference.getDataObject()).thenReturn(dataObject);
		when(reference.getType()).thenReturn(type);
		return reference;
	}

}
