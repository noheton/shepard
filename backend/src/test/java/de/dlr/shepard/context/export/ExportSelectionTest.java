package de.dlr.shepard.context.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.io.LabJournalEntryIO;
import de.dlr.shepard.context.labJournal.services.LabJournalEntryService;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.basicreference.services.BasicReferenceService;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.io.FileReferenceIO;
import de.dlr.shepard.context.references.file.services.FileBundleReferenceService;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.structureddata.io.StructuredDataReferenceIO;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.references.uri.services.URIReferenceService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class ExportSelectionTest {

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
  FileBundleReferenceService fileReferenceService;

  @InjectMock
  StructuredDataReferenceService structuredDataReferenceService;

  @InjectMock
  URIReferenceService uriReferenceService;

  @InjectMock
  LabJournalEntryService labJournalEntryService;

  @InjectMock
  AuthenticationContext authenticationContext;

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

    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(collection.getShepardId())).thenReturn(
      collection
    );
    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
  }

  private <T extends BasicReference> T hydrateReferenceMock(T reference, String type, long shepardId) {
    when(reference.getShepardId()).thenReturn(shepardId);
    when(reference.getCreatedBy()).thenReturn(user);
    when(reference.getCreatedAt()).thenReturn(dateHelper.getDate());
    when(reference.getDataObject()).thenReturn(dataObject);
    when(reference.getType()).thenReturn(type);
    return reference;
  }

  @Test
  public void selectionInclude_onlyFileReferenceKind_skipsTimeseriesAndStructured() throws IOException {
    var fileRef = hydrateReferenceMock(mock(FileBundleReference.class), "FileReference", 41L);
    var tsRef = hydrateReferenceMock(mock(TimeseriesReference.class), "TimeseriesReference", 42L);
    var sdRef = hydrateReferenceMock(mock(StructuredDataReference.class), "StructuredDataReference", 43L);
    dataObject.addReference(fileRef);
    dataObject.addReference(tsRef);
    dataObject.addReference(sdRef);

    when(
      fileReferenceService.getReference(collection.getShepardId(), dataObject.getShepardId(), 41L, null)
    ).thenReturn(fileRef);

    var sel = new ExportSelection(
      new ExportSelection.Payloads(Set.of(ExportSelection.PayloadKind.FileReference), null),
      null
    );

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (mock, ctx) -> {
        when(mock.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);

      var builderMock = ctrl.constructed().getFirst();
      verify(builderMock, atLeastOnce()).addReference(any(FileReferenceIO.class), eq(user));
      verify(builderMock, never()).addReference(any(TimeseriesReferenceIO.class), eq(user));
      verify(builderMock, never()).addReference(any(StructuredDataReferenceIO.class), eq(user));
    }
  }

  @Test
  public void selectionExcludeIds_skipsThatId_keepsOthers() throws IOException {
    var fileRef = hydrateReferenceMock(mock(FileBundleReference.class), "FileReference", 41L);
    var tsRef = hydrateReferenceMock(mock(TimeseriesReference.class), "TimeseriesReference", 42L);
    dataObject.addReference(fileRef);
    dataObject.addReference(tsRef);

    when(
      timeseriesReferenceService.getReference(collection.getShepardId(), dataObject.getShepardId(), 42L, null)
    ).thenReturn(tsRef);

    // Exclude the FileReference id; TimeseriesReference must still be included.
    var sel = new ExportSelection(new ExportSelection.Payloads(null, List.of("41")), null);

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (mock, ctx) -> {
        when(mock.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);

      var builderMock = ctrl.constructed().getFirst();
      verify(builderMock, never()).addReference(any(FileReferenceIO.class), eq(user));
      verify(builderMock, atLeastOnce()).addReference(any(TimeseriesReferenceIO.class), eq(user));
    }
  }

  @Test
  public void metadataLabJournalFalse_skipsLabJournalEntries() throws IOException {
    var entry = mock(LabJournalEntry.class);
    when(entry.getId()).thenReturn(99L);
    when(entry.getCreatedAt()).thenReturn(dateHelper.getDate());
    when(entry.getCreatedBy()).thenReturn(user);
    when(entry.getDataObject()).thenReturn(dataObject);
    dataObject.setLabJournalEntries(List.of(entry));
    when(labJournalEntryService.getLabJournalEntry(99L)).thenReturn(entry);

    var sel = new ExportSelection(null, new ExportSelection.Metadata(null, null, false, null, null));

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (mock, ctx) -> {
        when(mock.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);

      var builderMock = ctrl.constructed().getFirst();
      verify(builderMock, never()).addLabJournalEntry(any(LabJournalEntryIO.class), any());
    }
  }

  @Test
  public void metadataLabJournalDefault_includesLabJournalEntries() throws IOException {
    var entry = mock(LabJournalEntry.class);
    when(entry.getId()).thenReturn(99L);
    when(entry.getCreatedAt()).thenReturn(dateHelper.getDate());
    when(entry.getCreatedBy()).thenReturn(user);
    when(entry.getDataObject()).thenReturn(dataObject);
    dataObject.setLabJournalEntries(List.of(entry));
    when(labJournalEntryService.getLabJournalEntry(99L)).thenReturn(entry);

    // Default selection (no metadata) ⇒ labJournal still included (matches today's behaviour).
    var sel = new ExportSelection(
      new ExportSelection.Payloads(Set.of(ExportSelection.PayloadKind.BasicReference), null),
      null
    );

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (mock, ctx) -> {
        when(mock.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);

      var builderMock = ctrl.constructed().getFirst();
      verify(builderMock, atLeastOnce()).addLabJournalEntry(any(LabJournalEntryIO.class), any());
    }
  }

  @Test
  public void nullSelection_legacyExportBehaviourPreserved() throws IOException {
    var basicRef = hydrateReferenceMock(mock(BasicReference.class), "BasicReference", 41L);
    dataObject.addReference(basicRef);
    when(
      basicReferenceService.getReference(collection.getShepardId(), dataObject.getShepardId(), 41L)
    ).thenReturn(basicRef);

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (mock, ctx) -> {
        when(mock.build()).thenReturn(mockStream);
      })
    ) {
      InputStream actual = service.exportCollectionByShepardId(collection.getShepardId());

      var builderMock = ctrl.constructed().getFirst();
      verify(builderMock).addReference(any(BasicReferenceIO.class), eq(user));
      assertEquals(mockStream, actual);
    }
  }
}
