package de.dlr.shepard.context.export;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.ShepardException;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.io.LabJournalEntryIO;
import de.dlr.shepard.context.labJournal.services.LabJournalEntryService;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.basicreference.services.BasicReferenceService;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.io.FileReferenceIO;
import de.dlr.shepard.context.references.file.services.FileReferenceService;
import de.dlr.shepard.context.references.structureddata.io.StructuredDataReferenceIO;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.references.uri.io.URIReferenceIO;
import de.dlr.shepard.context.references.uri.services.URIReferenceService;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import de.dlr.shepard.data.timeseries.model.enums.CsvFormat;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.util.Collections;
import java.util.List;

@RequestScoped
public class ExportService {

  @Inject
  CollectionService collectionService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  BasicReferenceService basicReferenceService;

  @Inject
  LabJournalEntryService labJournalEntryService;

  @Inject
  TimeseriesReferenceService timeseriesReferenceService;

  @Inject
  FileReferenceService fileReferenceService;

  @Inject
  StructuredDataReferenceService structuredDataReferenceService;

  @Inject
  URIReferenceService uriReferenceService;

  @Inject
  AuthenticationContext authenticationContext;

  /**
   * Exports collection by shepard Id
   *
   * @param collectionId
   * @return InputStream
   * @throws InvalidPathException if collection with 'collectionId' could not be found
   * @throws InvalidAuthException if user has no read permissions on collection
   * @throws IOException if building the InputStream fails
   */
  public InputStream exportCollectionByShepardId(long collectionId) throws IOException {
    return exportCollectionByShepardId(collectionId, null);
  }

  /**
   * Exports collection by shepard Id with an optional selection filter.
   *
   * @param collectionId collection shepard id
   * @param selection optional selection (nullable ⇒ byte-identical legacy export)
   * @return InputStream zipped RO-Crate
   */
  public InputStream exportCollectionByShepardId(long collectionId, ExportSelection selection) throws IOException {
    Collection collection = collectionService.getCollectionWithDataObjectsAndIncomingReferences(collectionId);

    var exportBuilder = new ExportBuilder(collection, selection);
    for (var dataObject : collection.getDataObjects()) {
      fetchAndWriteDataObject(collectionId, exportBuilder, dataObject.getShepardId(), selection);
    }
    return exportBuilder.build();
  }

  private void fetchAndWriteDataObject(
    long collectionId,
    ExportBuilder builder,
    long dataObjectId,
    ExportSelection selection
  ) throws IOException, InvalidBodyException {
    var dataObject = dataObjectService.getDataObject(dataObjectId);
    builder.addDataObject(dataObject);

    // TODO: Add more types, maybe improve (StrategyPattern?)
    for (BasicReference reference : dataObject.getReferences()) {
      ExportSelection.PayloadKind kind = mapKind(reference.getType());
      if (selection != null && !selection.includesKind(kind)) continue;
      if (selection != null && selection.excludesId(String.valueOf(reference.getShepardId()))) continue;
      switch (reference.getType()) {
        case "TimeseriesReference" -> fetchAndWriteTimeseriesReference(
          collectionId,
          dataObjectId,
          builder,
          reference.getShepardId(),
          authenticationContext.getCurrentUserName()
        );
        case "FileReference" -> fetchAndWriteFileReference(
          collectionId,
          dataObjectId,
          builder,
          reference.getShepardId()
        );
        case "StructuredDataReference" -> fetchAndWriteStructuredDataReference(
          collectionId,
          dataObjectId,
          builder,
          reference.getShepardId()
        );
        case "URIReference" -> fetchAndWriteUriReference(
          collectionId,
          dataObjectId,
          builder,
          reference.getShepardId(),
          authenticationContext.getCurrentUserName()
        );
        default -> fetchAndWriteBasicReference(collectionId, dataObjectId, builder, reference.getShepardId());
      }
    }
    if (selection == null || selection.includeLabJournal()) {
      for (LabJournalEntry entry : dataObject.getLabJournalEntries()) {
        fetchAndWriteLabJournalEntry(collectionId, dataObjectId, builder, entry.getId());
      }
    }
  }

  private static ExportSelection.PayloadKind mapKind(String type) {
    if (type == null) return ExportSelection.PayloadKind.BasicReference;
    return switch (type) {
      case "TimeseriesReference" -> ExportSelection.PayloadKind.TimeseriesReference;
      case "FileReference" -> ExportSelection.PayloadKind.FileReference;
      case "StructuredDataReference" -> ExportSelection.PayloadKind.StructuredDataReference;
      case "URIReference" -> ExportSelection.PayloadKind.URIReference;
      default -> ExportSelection.PayloadKind.BasicReference;
    };
  }

  private void fetchAndWriteLabJournalEntry(
    long collectionId,
    long dataObjectId,
    ExportBuilder builder,
    long labJournalEntryId
  ) throws IOException {
    LabJournalEntry entry = labJournalEntryService.getLabJournalEntry(labJournalEntryId);
    builder.addLabJournalEntry(new LabJournalEntryIO(entry), entry.getCreatedBy());
  }

  private void fetchAndWriteTimeseriesReference(
    long collectionShepardId,
    long dataObjectShepardId,
    ExportBuilder builder,
    long referenceId,
    String username
  ) throws IOException {
    var reference = timeseriesReferenceService.getReference(
      collectionShepardId,
      dataObjectShepardId,
      referenceId,
      null
    );
    builder.addReference(new TimeseriesReferenceIO(reference), reference.getCreatedBy());
    InputStream timeseriesPayload = null;
    try {
      timeseriesPayload = timeseriesReferenceService.exportReferencedTimeseriesByShepardId(
        collectionShepardId,
        dataObjectShepardId,
        referenceId,
        CsvFormat.ROW
      );
    } catch (ShepardException e) {
      Log.warn("Cannot access timeseries payload during export");
    }
    if (timeseriesPayload != null) {
      writeTimeseriesPayload(builder, timeseriesPayload, reference);
    }
  }

  private void fetchAndWriteFileReference(
    long collectionShepardId,
    long dataObjectShepardId,
    ExportBuilder builder,
    long referenceId
  ) throws IOException {
    FileReference reference = fileReferenceService.getReference(
      collectionShepardId,
      dataObjectShepardId,
      referenceId,
      null
    );

    builder.addReference(new FileReferenceIO(reference), reference.getCreatedBy());

    List<NamedInputStream> payloads = Collections.emptyList();
    try {
      payloads = fileReferenceService.getAllPayloads(collectionShepardId, dataObjectShepardId, referenceId);
    } catch (ShepardException e) {
      Log.warn("Cannot access file payload during export");
    }

    for (var nis : payloads) {
      if (nis.getInputStream() != null) writeFilePayload(builder, nis);
    }
  }

  private void fetchAndWriteStructuredDataReference(
    long collectionId,
    long dataObjectId,
    ExportBuilder builder,
    long referenceId
  ) throws IOException {
    var reference = structuredDataReferenceService.getReference(collectionId, dataObjectId, referenceId, null);

    builder.addReference(new StructuredDataReferenceIO(reference), reference.getCreatedBy());

    List<StructuredDataPayload> payloads = Collections.emptyList();
    try {
      payloads = structuredDataReferenceService.getAllPayloads(collectionId, dataObjectId, referenceId);
    } catch (ShepardException e) {
      Log.warn("Cannot access structured data payload during export");
    }

    for (var sdp : payloads) {
      if (sdp.getPayload() != null) writeStructuredDataPayload(builder, sdp);
    }
  }

  private void fetchAndWriteUriReference(
    long collectionShepardId,
    long dataObjectShepardId,
    ExportBuilder builder,
    long referenceId,
    String username
  ) throws IOException {
    var reference = uriReferenceService.getReference(collectionShepardId, dataObjectShepardId, referenceId, null);

    builder.addReference(new URIReferenceIO(reference), reference.getCreatedBy());
  }

  private void fetchAndWriteBasicReference(
    long collectionShepardId,
    long dataObjectShepardId,
    ExportBuilder builder,
    long referenceId
  ) throws IOException {
    var reference = basicReferenceService.getReference(collectionShepardId, dataObjectShepardId, referenceId);

    builder.addReference(new BasicReferenceIO(reference), reference.getCreatedBy());
  }

  private void writeFilePayload(ExportBuilder builder, NamedInputStream nis) throws IOException {
    var nameSplitted = nis.getName().split("\\.", 2);
    var filename = nameSplitted.length > 1 ? nis.getOid() + "." + nameSplitted[1] : nis.getOid();

    builder.addPayload(nis.getInputStream().readAllBytes(), filename, nis.getName());
  }

  private void writeStructuredDataPayload(ExportBuilder builder, StructuredDataPayload sdp) throws IOException {
    var filename = sdp.getStructuredData().getOid() + ExportConstants.JSON_FILE_EXTENSION;

    builder.addPayload(sdp.getPayload().getBytes(), filename, sdp.getStructuredData().getName(), "application/json");
  }

  private void writeTimeseriesPayload(ExportBuilder builder, InputStream payload, TimeseriesReference reference)
    throws IOException {
    var filename = reference.getUniqueId() + ExportConstants.CSV_FILE_EXTENSION;

    builder.addPayload(payload.readAllBytes(), filename, reference.getName(), "text/csv");
  }
}
