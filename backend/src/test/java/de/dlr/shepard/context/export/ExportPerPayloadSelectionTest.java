package de.dlr.shepard.context.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.labJournal.services.LabJournalEntryService;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.services.BasicReferenceService;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.io.FileReferenceIO;
import de.dlr.shepard.context.references.file.services.FileReferenceService;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.references.uri.services.URIReferenceService;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.timeseries.model.enums.CsvFormat;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class ExportPerPayloadSelectionTest {

  DateHelper dateHelper = new DateHelper();
  private final ObjectMapper objectMapper = new ObjectMapper();

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

  @InjectMock
  LabJournalEntryService labJournalEntryService;

  @InjectMock
  AuthenticationContext authenticationContext;

  @Inject
  ExportService service;

  private final User user = new User("bob");
  private DataObject dataObject;
  private Collection collection;

  @BeforeEach
  void initMocks() {
    dataObject = new DataObject() {
      {
        setShepardId(25L);
        setCreatedAt(dateHelper.getDate());
        setCreatedBy(user);
      }
    };
    collection = new Collection() {
      {
        setShepardId(15L);
        setCreatedAt(dateHelper.getDate());
        setCreatedBy(user);
      }
    };
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

  // ---------- FileReference: fileOids filter ------------------------------

  @Test
  public void fileOids_includesOnlyMatchingOids_andEmitsBytesViaPerOidApi() throws IOException {
    var fileRef = hydrateReferenceMock(mock(FileReference.class), "FileReference", 41L);
    var fileA = new ShepardFile("oid-A", dateHelper.getDate(), "a.txt", null);
    var fileB = new ShepardFile("oid-B", dateHelper.getDate(), "b.txt", null);
    var fileC = new ShepardFile("oid-C", dateHelper.getDate(), "c.txt", null);
    when(fileRef.getFiles()).thenReturn(List.of(fileA, fileB, fileC));
    dataObject.addReference(fileRef);

    when(fileReferenceService.getReference(collection.getShepardId(), dataObject.getShepardId(), 41L, null)).thenReturn(
      fileRef
    );
    when(
      fileReferenceService.getPayload(eq(collection.getShepardId()), eq(dataObject.getShepardId()), eq(41L), any(), isNull())
    ).thenAnswer(inv -> {
      String oid = inv.getArgument(3);
      return new NamedInputStream(oid, new ByteArrayInputStream(("data-" + oid).getBytes()), oid + ".txt", 6L);
    });

    var sel = new ExportSelection(
      new ExportSelection.Payloads(null, null, Map.of("41", new ExportSelection.PerPayloadSelection(List.of("oid-A", "oid-B"), null, null)), null),
      null
    );

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);

      var builder = ctrl.constructed().getFirst();
      // Per-OID API used twice (oid-A and oid-B), getAllPayloads must NOT be called.
      verify(fileReferenceService, times(2)).getPayload(
        eq(collection.getShepardId()),
        eq(dataObject.getShepardId()),
        eq(41L),
        any(),
        isNull()
      );
      verify(fileReferenceService, never()).getAllPayloads(anyLong3(), anyLong3(), anyLong3());
      // Two payload bytes flushed (only A and B).
      verify(builder, times(2)).addPayload(any(byte[].class), any(), any());
    }
  }

  // helper for matchers — Mockito doesn't expose a convenient anyLong overload, just use anyLong()
  private static long anyLong3() {
    return org.mockito.ArgumentMatchers.anyLong();
  }

  @Test
  public void fileOids_staleOid_silentlySkipped_andRecordedAsWarning() throws IOException {
    var fileRef = hydrateReferenceMock(mock(FileReference.class), "FileReference", 41L);
    var fileA = new ShepardFile("oid-A", dateHelper.getDate(), "a.txt", null);
    when(fileRef.getFiles()).thenReturn(List.of(fileA));
    dataObject.addReference(fileRef);

    when(fileReferenceService.getReference(collection.getShepardId(), dataObject.getShepardId(), 41L, null)).thenReturn(
      fileRef
    );
    when(
      fileReferenceService.getPayload(eq(collection.getShepardId()), eq(dataObject.getShepardId()), eq(41L), eq("oid-A"), isNull())
    ).thenReturn(new NamedInputStream("oid-A", new ByteArrayInputStream("a-bytes".getBytes()), "a.txt", 7L));

    var sel = new ExportSelection(
      new ExportSelection.Payloads(
        null,
        null,
        Map.of("41", new ExportSelection.PerPayloadSelection(List.of("oid-A", "oid-MISSING"), null, null)),
        null
      ),
      null
    );

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);

      var builder = ctrl.constructed().getFirst();
      verify(builder).addSelectionWarning(org.mockito.ArgumentMatchers.contains("stale file OIDs skipped"));
      // Only oid-A was actually fetched.
      verify(fileReferenceService, times(1)).getPayload(
        eq(collection.getShepardId()),
        eq(dataObject.getShepardId()),
        eq(41L),
        eq("oid-A"),
        isNull()
      );
    }
  }

  @Test
  public void fileOids_strictMode_staleOid_throwsBadRequest() throws IOException {
    var fileRef = hydrateReferenceMock(mock(FileReference.class), "FileReference", 41L);
    var fileA = new ShepardFile("oid-A", dateHelper.getDate(), "a.txt", null);
    when(fileRef.getFiles()).thenReturn(List.of(fileA));
    dataObject.addReference(fileRef);

    when(fileReferenceService.getReference(collection.getShepardId(), dataObject.getShepardId(), 41L, null)).thenReturn(
      fileRef
    );

    var sel = new ExportSelection(
      new ExportSelection.Payloads(
        null,
        null,
        Map.of("41", new ExportSelection.PerPayloadSelection(List.of("oid-MISSING"), null, null)),
        true
      ),
      null
    );

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      assertThrows(
        BadRequestException.class,
        () -> service.exportCollectionByShepardId(collection.getShepardId(), sel)
      );
    }
  }

  // ---------- TimeseriesReference: columns + timeRange --------------------

  @Test
  public void columns_filteredFieldFilterPassedToService_andUnknownColumnsRecorded() throws IOException {
    var tsRef = hydrateReferenceMock(mock(TimeseriesReference.class), "TimeseriesReference", 42L);
    when(tsRef.getReferencedTimeseriesList()).thenReturn(
      List.of(
        new ReferencedTimeseriesNodeEntity("m", "d", "l", "s", "temp"),
        new ReferencedTimeseriesNodeEntity("m", "d", "l", "s", "pressure"),
        new ReferencedTimeseriesNodeEntity("m", "d", "l", "s", "humidity")
      )
    );
    dataObject.addReference(tsRef);

    when(
      timeseriesReferenceService.getReference(collection.getShepardId(), dataObject.getShepardId(), 42L, null)
    ).thenReturn(tsRef);
    when(
      timeseriesReferenceService.exportReferencedTimeseriesByShepardId(
        anyLong3(),
        anyLong3(),
        anyLong3(),
        any(),
        any(),
        any(),
        anySet(),
        anySet(),
        anySet(),
        anySet(),
        anySet(),
        any(CsvFormat.class),
        any(),
        any()
      )
    ).thenReturn(new ByteArrayInputStream("ts,temp,pressure\n0,1,2\n".getBytes()));

    var sel = new ExportSelection(
      new ExportSelection.Payloads(
        null,
        null,
        Map.of(
          "42",
          new ExportSelection.PerPayloadSelection(null, List.of("temp", "pressure", "BOGUS"), null)
        ),
        null
      ),
      null
    );

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);

      var builder = ctrl.constructed().getFirst();
      verify(builder).addSelectionWarning(org.mockito.ArgumentMatchers.contains("unknown columns skipped"));
      // The service must have been called with field filter == {temp, pressure} and no time override.
      verify(timeseriesReferenceService).exportReferencedTimeseriesByShepardId(
        eq(collection.getShepardId()),
        eq(dataObject.getShepardId()),
        eq(42L),
        isNull(),
        isNull(),
        isNull(),
        eq(java.util.Collections.emptySet()),
        eq(java.util.Collections.emptySet()),
        eq(java.util.Collections.emptySet()),
        eq(java.util.Collections.emptySet()),
        eq(Set.of("temp", "pressure")),
        eq(CsvFormat.ROW),
        isNull(),
        isNull()
      );
    }
  }

  @Test
  public void timeRange_passedAsNanosOverrideToService() throws IOException {
    var tsRef = hydrateReferenceMock(mock(TimeseriesReference.class), "TimeseriesReference", 42L);
    when(tsRef.getReferencedTimeseriesList()).thenReturn(List.of());
    dataObject.addReference(tsRef);

    when(
      timeseriesReferenceService.getReference(collection.getShepardId(), dataObject.getShepardId(), 42L, null)
    ).thenReturn(tsRef);
    when(
      timeseriesReferenceService.exportReferencedTimeseriesByShepardId(
        anyLong3(),
        anyLong3(),
        anyLong3(),
        any(),
        any(),
        any(),
        anySet(),
        anySet(),
        anySet(),
        anySet(),
        anySet(),
        any(CsvFormat.class),
        any(),
        any()
      )
    ).thenReturn(new ByteArrayInputStream("ts,x\n".getBytes()));

    Instant start = Instant.parse("2024-01-01T00:00:00Z");
    Instant end = Instant.parse("2024-12-31T23:59:59Z");
    long expectedStartNanos =
      Math.multiplyExact(start.getEpochSecond(), 1_000_000_000L) + start.getNano();
    long expectedEndNanos = Math.multiplyExact(end.getEpochSecond(), 1_000_000_000L) + end.getNano();

    var sel = new ExportSelection(
      new ExportSelection.Payloads(
        null,
        null,
        Map.of(
          "42",
          new ExportSelection.PerPayloadSelection(null, null, new ExportSelection.TimeRange(start, end))
        ),
        null
      ),
      null
    );

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);

      verify(timeseriesReferenceService).exportReferencedTimeseriesByShepardId(
        anyLong3(),
        anyLong3(),
        anyLong3(),
        any(),
        any(),
        any(),
        anySet(),
        anySet(),
        anySet(),
        anySet(),
        anySet(),
        any(CsvFormat.class),
        eq(expectedStartNanos),
        eq(expectedEndNanos)
      );
    }
  }

  @Test
  public void columnsAndTimeRange_combineCorrectly() throws IOException {
    var tsRef = hydrateReferenceMock(mock(TimeseriesReference.class), "TimeseriesReference", 42L);
    when(tsRef.getReferencedTimeseriesList()).thenReturn(
      List.of(new ReferencedTimeseriesNodeEntity("m", "d", "l", "s", "temp"))
    );
    dataObject.addReference(tsRef);

    when(
      timeseriesReferenceService.getReference(collection.getShepardId(), dataObject.getShepardId(), 42L, null)
    ).thenReturn(tsRef);
    when(
      timeseriesReferenceService.exportReferencedTimeseriesByShepardId(
        anyLong3(),
        anyLong3(),
        anyLong3(),
        any(),
        any(),
        any(),
        anySet(),
        anySet(),
        anySet(),
        anySet(),
        anySet(),
        any(CsvFormat.class),
        any(),
        any()
      )
    ).thenReturn(new ByteArrayInputStream("ts,temp\n".getBytes()));

    Instant start = Instant.parse("2024-01-01T00:00:00Z");
    long expectedStartNanos =
      Math.multiplyExact(start.getEpochSecond(), 1_000_000_000L) + start.getNano();

    var sel = new ExportSelection(
      new ExportSelection.Payloads(
        null,
        null,
        Map.of(
          "42",
          new ExportSelection.PerPayloadSelection(
            null,
            List.of("temp"),
            new ExportSelection.TimeRange(start, null)
          )
        ),
        null
      ),
      null
    );

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);

      verify(timeseriesReferenceService).exportReferencedTimeseriesByShepardId(
        eq(collection.getShepardId()),
        eq(dataObject.getShepardId()),
        eq(42L),
        isNull(),
        isNull(),
        isNull(),
        eq(java.util.Collections.emptySet()),
        eq(java.util.Collections.emptySet()),
        eq(java.util.Collections.emptySet()),
        eq(java.util.Collections.emptySet()),
        eq(Set.of("temp")),
        eq(CsvFormat.ROW),
        eq(expectedStartNanos),
        isNull()
      );
    }
  }

  @Test
  public void columns_strictMode_unknownColumn_throwsBadRequest() throws IOException {
    var tsRef = hydrateReferenceMock(mock(TimeseriesReference.class), "TimeseriesReference", 42L);
    when(tsRef.getReferencedTimeseriesList()).thenReturn(
      List.of(new ReferencedTimeseriesNodeEntity("m", "d", "l", "s", "temp"))
    );
    dataObject.addReference(tsRef);

    when(
      timeseriesReferenceService.getReference(collection.getShepardId(), dataObject.getShepardId(), 42L, null)
    ).thenReturn(tsRef);

    var sel = new ExportSelection(
      new ExportSelection.Payloads(
        null,
        null,
        Map.of("42", new ExportSelection.PerPayloadSelection(null, List.of("BOGUS"), null)),
        true
      ),
      null
    );

    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mock(InputStream.class));
      })
    ) {
      assertThrows(
        BadRequestException.class,
        () -> service.exportCollectionByShepardId(collection.getShepardId(), sel)
      );
    }
  }

  // ---------- Mismatched-kind perPayload entries silently ignored ---------

  @Test
  public void perPayload_fileOidsOnTimeseriesId_silentlyIgnored() throws IOException {
    var tsRef = hydrateReferenceMock(mock(TimeseriesReference.class), "TimeseriesReference", 42L);
    when(tsRef.getReferencedTimeseriesList()).thenReturn(List.of());
    dataObject.addReference(tsRef);

    when(
      timeseriesReferenceService.getReference(collection.getShepardId(), dataObject.getShepardId(), 42L, null)
    ).thenReturn(tsRef);
    when(
      timeseriesReferenceService.exportReferencedTimeseriesByShepardId(
        anyLong3(),
        anyLong3(),
        anyLong3(),
        any(),
        any(),
        any(),
        anySet(),
        anySet(),
        anySet(),
        anySet(),
        anySet(),
        any(CsvFormat.class),
        any(),
        any()
      )
    ).thenReturn(new ByteArrayInputStream("ts\n".getBytes()));

    // fileOids set on a TimeseriesReference id ⇒ field is not applicable, must be ignored.
    var sel = new ExportSelection(
      new ExportSelection.Payloads(
        null,
        null,
        Map.of(
          "42",
          new ExportSelection.PerPayloadSelection(List.of("does-not-matter"), null, null)
        ),
        null
      ),
      null
    );

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      // Must not throw, must not warn — fileOids on a non-File kind is a no-op.
      service.exportCollectionByShepardId(collection.getShepardId(), sel);
      var builder = ctrl.constructed().getFirst();
      verify(builder, never()).addSelectionWarning(any());
    }
  }

  // ---------- Empty perPayload ⇒ regression with R2 Phase 1 + R2d ---------

  @Test
  public void emptyPerPayloadMap_keepsLegacyFullFetchPath() throws IOException {
    var fileRef = hydrateReferenceMock(mock(FileReference.class), "FileReference", 41L);
    var fileA = new ShepardFile("oid-A", dateHelper.getDate(), "a.txt", null);
    when(fileRef.getFiles()).thenReturn(List.of(fileA));
    dataObject.addReference(fileRef);

    when(fileReferenceService.getReference(collection.getShepardId(), dataObject.getShepardId(), 41L, null)).thenReturn(
      fileRef
    );
    when(
      fileReferenceService.getAllPayloads(collection.getShepardId(), dataObject.getShepardId(), 41L)
    ).thenReturn(
      List.of(new NamedInputStream("oid-A", new ByteArrayInputStream("a-bytes".getBytes()), "a.txt", 7L))
    );

    var sel = new ExportSelection(new ExportSelection.Payloads(null, null, Map.of(), null), null);

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);

      // Legacy bulk fetch path used; per-OID API not consulted.
      verify(fileReferenceService).getAllPayloads(collection.getShepardId(), dataObject.getShepardId(), 41L);
      verify(fileReferenceService, never()).getPayload(anyLong3(), anyLong3(), anyLong3(), any(), any());
    }
  }

  // ---------- selection.warnings end-to-end through real ExportBuilder ----

  @Test
  public void selectionWarnings_landInManifestUnderSelectionBlock() throws IOException {
    var builder = new ExportBuilder(
      collection,
      new ExportSelection(
        new ExportSelection.Payloads(
          null,
          null,
          Map.of("41", new ExportSelection.PerPayloadSelection(List.of("oid-MISSING"), null, null)),
          null
        ),
        null
      )
    );
    builder.addSelectionWarning("FileReference id=41: stale file OIDs skipped [oid-MISSING]");
    var entries = readZip(builder.build());
    JsonNode tree = objectMapper.readTree(entries.get(ExportConstants.ROCRATE_METADATA));
    JsonNode root = findRoot(tree);
    assertTrue(root.has("selection"), "selection block must be present");
    JsonNode warnings = root.get("selection").get("warnings");
    assertNotNull(warnings, "warnings array must be present");
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).asText().contains("oid-MISSING"));
  }

  @Test
  public void emptySelection_noWarnings_byteIdenticalLegacyManifest() throws IOException {
    var legacy = new ExportBuilder(collection);
    var emptySelection = new ExportBuilder(collection, new ExportSelection(null, null));
    byte[] legacyBytes = readZip(legacy.build()).get(ExportConstants.ROCRATE_METADATA);
    byte[] emptyBytes = readZip(emptySelection.build()).get(ExportConstants.ROCRATE_METADATA);
    assertEquals(objectMapper.readTree(legacyBytes), objectMapper.readTree(emptyBytes));
    assertFalse(findRoot(objectMapper.readTree(emptyBytes)).has("selection"));
  }

  // ---------- helpers -----------------------------------------------------

  private Map<String, byte[]> readZip(InputStream zipStream) throws IOException {
    Map<String, byte[]> result = new HashMap<>();
    try (var zis = new ZipInputStream(zipStream)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        var bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = zis.read(buf)) > 0) bos.write(buf, 0, n);
        result.put(entry.getName(), bos.toByteArray());
      }
    }
    return result;
  }

  private JsonNode findRoot(JsonNode tree) {
    JsonNode graph = tree.get("@graph");
    assertNotNull(graph);
    for (JsonNode node : graph) {
      JsonNode id = node.get("@id");
      if (id != null && "./".equals(id.asText())) return node;
    }
    throw new AssertionError("root data entity not found");
  }
}
