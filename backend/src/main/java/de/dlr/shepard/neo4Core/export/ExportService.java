package de.dlr.shepard.neo4Core.export;

import de.dlr.shepard.exceptions.InvalidAuthException;
import de.dlr.shepard.mongoDB.NamedInputStream;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.neo4Core.entities.TimeseriesReference;
import de.dlr.shepard.neo4Core.io.BasicReferenceIO;
import de.dlr.shepard.neo4Core.io.FileReferenceIO;
import de.dlr.shepard.neo4Core.io.StructuredDataReferenceIO;
import de.dlr.shepard.neo4Core.io.TimeseriesReferenceIO;
import de.dlr.shepard.neo4Core.io.URIReferenceIO;
import de.dlr.shepard.neo4Core.services.BasicReferenceService;
import de.dlr.shepard.neo4Core.services.CollectionService;
import de.dlr.shepard.neo4Core.services.DataObjectService;
import de.dlr.shepard.neo4Core.services.FileReferenceService;
import de.dlr.shepard.neo4Core.services.StructuredDataReferenceService;
import de.dlr.shepard.neo4Core.services.TimeseriesReferenceService;
import de.dlr.shepard.neo4Core.services.URIReferenceService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestScoped
public class ExportService {

  private CollectionService collectionService;
  private DataObjectService dataObjectService;
  private BasicReferenceService basicReferenceService;
  private TimeseriesReferenceService timeseriesReferenceService;
  private FileReferenceService fileReferenceService;
  private StructuredDataReferenceService structuredDataReferenceService;
  private URIReferenceService uriReferenceService;

  ExportService() {}

  @Inject
  public ExportService(
    CollectionService collectionService,
    DataObjectService dataObjectService,
    BasicReferenceService basicReferenceService,
    TimeseriesReferenceService timeseriesReferenceService,
    FileReferenceService fileReferenceService,
    StructuredDataReferenceService structuredDataReferenceService,
    URIReferenceService uriReferenceService
  ) {
    this.collectionService = collectionService;
    this.dataObjectService = dataObjectService;
    this.basicReferenceService = basicReferenceService;
    this.timeseriesReferenceService = timeseriesReferenceService;
    this.fileReferenceService = fileReferenceService;
    this.structuredDataReferenceService = structuredDataReferenceService;
    this.uriReferenceService = uriReferenceService;
  }

  public InputStream exportCollectionByShepardId(long collectionId, String username) throws IOException {
    var collection = collectionService.getCollectionByShepardId(collectionId);

    var builder = new ExportBuilder(collection);
    for (var dataObject : collection.getDataObjects()) {
      fetchAndWriteDataObject(builder, dataObject.getShepardId(), username);
    }
    return builder.build();
  }

  private void fetchAndWriteDataObject(ExportBuilder builder, long dataObjectId, String username) throws IOException {
    var dataObject = dataObjectService.getDataObjectByShepardId(dataObjectId);
    builder.addDataObject(dataObject);

    // TODO: Add more types, maybe improve (StrategyPattern?)
    for (var reference : dataObject.getReferences()) {
      switch (reference.getType()) {
        case "TimeseriesReference" -> fetchAndWriteTimeseriesReference(builder, reference.getShepardId(), username);
        case "FileReference" -> fetchAndWriteFileReference(builder, reference.getShepardId(), username);
        case "StructuredDataReference" -> fetchAndWriteStructuredDataReference(
          builder,
          reference.getShepardId(),
          username
        );
        case "URIReference" -> fetchAndWriteUriReference(builder, reference.getShepardId(), username);
        default -> fetchAndWriteBasicReference(builder, reference.getShepardId());
      }
    }
  }

  private void fetchAndWriteTimeseriesReference(ExportBuilder builder, long referenceId, String username)
    throws IOException {
    var reference = timeseriesReferenceService.getReferenceByShepardId(referenceId);

    builder.addReference(new TimeseriesReferenceIO(reference), reference.getCreatedBy());

    InputStream timeseriesPayload = null;
    try {
      timeseriesPayload = timeseriesReferenceService.exportTimeseriesPayloadByShepardId(referenceId, username);
    } catch (InvalidAuthException e) {
      log.warn("Cannot access timeseries payload during export due to invalid permissions");
    }
    if (timeseriesPayload != null) {
      writeTimeseriesPayload(builder, timeseriesPayload, reference);
    }
  }

  private void fetchAndWriteFileReference(ExportBuilder builder, long referenceId, String username) throws IOException {
    var reference = fileReferenceService.getReferenceByShepardId(referenceId);

    builder.addReference(new FileReferenceIO(reference), reference.getCreatedBy());

    List<NamedInputStream> payloads = Collections.emptyList();
    try {
      payloads = fileReferenceService.getAllPayloadsByShepardId(referenceId, username);
    } catch (InvalidAuthException e) {
      log.warn("Cannot access file payload during export due to invalid permissions");
    }

    for (var nis : payloads) {
      writeFilePayload(builder, nis);
    }
  }

  private void fetchAndWriteStructuredDataReference(ExportBuilder builder, long referenceId, String username)
    throws IOException {
    var reference = structuredDataReferenceService.getReferenceByShepardId(referenceId);

    builder.addReference(new StructuredDataReferenceIO(reference), reference.getCreatedBy());

    List<StructuredDataPayload> payloads = Collections.emptyList();
    try {
      // filter empty payloads (due to invalid permissions)
      payloads = structuredDataReferenceService
        .getAllPayloadsByShepardId(referenceId, username)
        .stream()
        .filter(p -> p.getPayload() != null)
        .toList();
    } catch (InvalidAuthException e) {
      log.warn("Cannot access structured data payload during export due to invalid permissions");
    }

    for (var sdp : payloads) {
      writeStructuredDataPayload(builder, sdp);
    }
  }

  private void fetchAndWriteUriReference(ExportBuilder builder, long referenceId, String username) throws IOException {
    var reference = uriReferenceService.getReferenceByShepardId(referenceId);

    builder.addReference(new URIReferenceIO(reference), reference.getCreatedBy());
  }

  private void fetchAndWriteBasicReference(ExportBuilder builder, long referenceId) throws IOException {
    var reference = basicReferenceService.getReferenceByShepardId(referenceId);

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
