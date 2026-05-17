package de.dlr.shepard.context.export;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.basicreference.services.BasicReferenceService;
import de.dlr.shepard.context.references.file.services.FileBundleReferenceService;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.references.uri.services.URIReferenceService;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.services.SnapshotService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V2d — unit tests for snapshot-pinned export filtering in {@link ExportService}.
 *
 * <p>Three cases:
 * <ol>
 *   <li>{@code snapshotAppId} set, snapshot found — only DataObjects in the snapshot are exported.</li>
 *   <li>{@code snapshotAppId} set, snapshot NOT found — conservative: export nothing.</li>
 *   <li>{@code snapshotAppId} absent (null) — full export, all DataObjects included.</li>
 * </ol>
 */
@QuarkusComponentTest
public class ExportSnapshotFilterTest {

  DateHelper dateHelper = new DateHelper();

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
  AuthenticationContext authenticationContext;

  @InjectMock
  SnapshotService snapshotService;

  @Inject
  ExportService service;

  private final User user = new User("bob");

  /** DataObject whose appId IS in the snapshot. */
  private final DataObject includedDataObject = new DataObject() {
    {
      setShepardId(25L);
      setAppId("do-included");
      setCreatedAt(new DateHelper().getDate());
      setCreatedBy(user);
    }
  };

  /** DataObject whose appId is NOT in the snapshot. */
  private final DataObject excludedDataObject = new DataObject() {
    {
      setShepardId(26L);
      setAppId("do-excluded");
      setCreatedAt(new DateHelper().getDate());
      setCreatedBy(user);
    }
  };

  private final Collection collection = new Collection() {
    {
      setShepardId(15L);
      setCreatedAt(new DateHelper().getDate());
      setCreatedBy(user);
    }
  };

  private final Snapshot snapshot = new Snapshot();

  @BeforeEach
  void initMocks() {
    collection.addDataObject(includedDataObject);
    collection.addDataObject(excludedDataObject);
    includedDataObject.setCollection(collection);
    excludedDataObject.setCollection(collection);

    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(collection.getShepardId())).thenReturn(
      collection
    );
    when(dataObjectService.getDataObject(includedDataObject.getShepardId())).thenReturn(includedDataObject);
    when(dataObjectService.getDataObject(excludedDataObject.getShepardId())).thenReturn(excludedDataObject);
  }

  /**
   * When {@code snapshotAppId} is set and the snapshot exists, only DataObjects
   * whose appId appears in the snapshot's DataObject list are exported. The other
   * DataObject is silently skipped.
   */
  @Test
  public void snapshotFilter_excludesDataObjectsNotInSnapshot() throws IOException {
    when(snapshotService.findByAppId("snap-1")).thenReturn(snapshot);
    when(snapshotService.listDataObjectAppIds(snapshot)).thenReturn(List.of("do-included"));

    var selection = new ExportSelection("snap-1", null, null);
    var mockStream = org.mockito.Mockito.mock(InputStream.class);

    try (
      var exportBuilderMockController = mockConstruction(ExportBuilder.class, (mock, context) -> {
        when(mock.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), selection);

      var exportBuilderMock = exportBuilderMockController.constructed().getFirst();
      // Only the included DataObject should have been written
      verify(exportBuilderMock).addDataObject(includedDataObject);
      verify(exportBuilderMock, never()).addDataObject(excludedDataObject);
    }
  }

  /**
   * When {@code snapshotAppId} is set but the snapshot is not found, the export
   * should produce an empty body — no DataObjects exported (conservative guard
   * against exporting potentially stale data when the snapshot can't be resolved).
   */
  @Test
  public void snapshotFilter_unknownSnapshotAppId_exportsNothing() throws IOException {
    when(snapshotService.findByAppId("snap-unknown")).thenReturn(null);

    var selection = new ExportSelection("snap-unknown", null, null);
    var mockStream = org.mockito.Mockito.mock(InputStream.class);

    try (
      var exportBuilderMockController = mockConstruction(ExportBuilder.class, (mock, context) -> {
        when(mock.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), selection);

      var exportBuilderMock = exportBuilderMockController.constructed().getFirst();
      verify(exportBuilderMock, never()).addDataObject(any(DataObject.class));
    }
  }

  /**
   * When {@code snapshotAppId} is absent (null), the full-export behaviour is
   * preserved — all DataObjects in the collection are included.
   */
  @Test
  public void snapshotFilter_nullSnapshotAppId_includesAll() throws IOException {
    // snapshotService should NOT be consulted when snapshotAppId is null
    var selection = new ExportSelection(null, null, null);
    var mockStream = org.mockito.Mockito.mock(InputStream.class);

    try (
      var exportBuilderMockController = mockConstruction(ExportBuilder.class, (mock, context) -> {
        when(mock.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), selection);

      var exportBuilderMock = exportBuilderMockController.constructed().getFirst();
      verify(exportBuilderMock).addDataObject(includedDataObject);
      verify(exportBuilderMock).addDataObject(excludedDataObject);
      verify(snapshotService, never()).findByAppId(any());
    }
  }
}
