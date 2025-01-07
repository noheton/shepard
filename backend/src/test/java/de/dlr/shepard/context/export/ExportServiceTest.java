package de.dlr.shepard.context.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.basicreference.services.BasicReferenceService;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.io.FileReferenceIO;
import de.dlr.shepard.context.references.file.services.FileReferenceService;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.structureddata.io.StructuredDataReferenceIO;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import de.dlr.shepard.context.references.uri.services.URIReferenceService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class ExportServiceTest {

  // Hint: DateHelper is used in constructor and therefore cannot be injected.
  DateHelper dateHelper = new DateHelper();

  @InjectMock
  UserService userService;

  @InjectMock
  CollectionService collectionService;

  @InjectMock
  DataObjectService dataObjectService;

  @InjectMock
  BasicReferenceService basicReferenceService;

  @InjectMock
  TimeseriesReferenceService timeseriesReferenceService;

  @InjectMock
  FileReferenceService fileReferenceService;

  @InjectMock
  StructuredDataReferenceService structuredDataReferenceService;

  @InjectMock
  URIReferenceService uriReferenceService;

  @Inject
  ExportService service;

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
  void initMocks() {
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
    try (
      var exportBuilderMockController = mockConstruction(ExportBuilder.class, (mock, context) -> {
        when(mock.build()).thenReturn(mockStream);
      });
    ) {
      var actual = service.exportCollectionByShepardId(collection.getShepardId(), user.getUsername());

      var exportBuilderMock = exportBuilderMockController.constructed().get(0);
      verify(exportBuilderMock).addReference(any(BasicReferenceIO.class), eq(user));
      verify(exportBuilderMock).addDataObject(dataObject);

      assertEquals(1, exportBuilderMockController.constructed().size());
      assertEquals(mockStream, actual);
    }
  }

  @Test
  public void exportTest_uriReference() throws IOException {
    var reference = hydrateReferenceMock(mock(URIReference.class), "URIReference");
    dataObject.addReference(reference);
    when(uriReferenceService.getReferenceByShepardId(reference.getShepardId())).thenReturn(reference);

    var mockStream = mock(InputStream.class);
    try (
      var exportBuilderMockController = mockConstruction(ExportBuilder.class, (mock, context) -> {
        when(mock.build()).thenReturn(mockStream);
      });
    ) {
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
    try (
      var exportBuilderMockController = mockConstruction(ExportBuilder.class, (mock, context) -> {
        when(mock.build()).thenReturn(mockStream);
      });
    ) {
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
    try (
      var exportBuilderMockController = mockConstruction(ExportBuilder.class, (mock, context) -> {
        when(mock.build()).thenReturn(mockStream);
      });
    ) {
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
    try (
      var exportBuilderMockController = mockConstruction(ExportBuilder.class, (mock, context) -> {
        when(mock.build()).thenReturn(mockStream);
      });
    ) {
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
