package de.dlr.shepard.context.export;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.ShepardException;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.common.subscription.io.SubscriptionIO;
import de.dlr.shepard.common.subscription.services.SubscriptionService;
import de.dlr.shepard.common.util.RequestMethod;
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
import de.dlr.shepard.context.references.structureddata.io.StructuredDataReferenceIO;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.references.uri.io.URIReferenceIO;
import de.dlr.shepard.context.references.uri.services.URIReferenceService;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.version.io.VersionIO;
import de.dlr.shepard.context.version.services.VersionService;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import de.dlr.shepard.data.timeseries.model.enums.CsvFormat;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
  FileBundleReferenceService fileReferenceService;

  @Inject
  StructuredDataReferenceService structuredDataReferenceService;

  @Inject
  URIReferenceService uriReferenceService;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  PermissionsService permissionsService;

  @Inject
  VersionService versionService;

  @Inject
  SubscriptionService subscriptionService;

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
    writeEntityMetadata(exportBuilder, collection, selection, true);
    for (var dataObject : collection.getDataObjects()) {
      fetchAndWriteDataObject(collectionId, exportBuilder, dataObject.getShepardId(), selection);
    }
    return exportBuilder.build();
  }

  /**
   * Emit per-entity metadata documents (permissions / versions / annotations / subscriptions) when
   * the selection has the corresponding boolean flipped to {@code true}. Defaults are
   * {@code false} so legacy behaviour is unchanged.
   *
   * @param isCollection only collections get a versions document; the rest skip that kind.
   */
  private void writeEntityMetadata(
    ExportBuilder builder,
    BasicEntity entity,
    ExportSelection selection,
    boolean isCollection
  ) throws IOException {
    if (selection == null) return;
    long ioId = entity.getNumericId();
    if (selection.includePermissions()) {
      var perms = permissionsService
        .getPermissionsOfEntityOptional(entity.getId())
        .map(PermissionsIO::new)
        .orElse(null);
      builder.addPermissionsFor(ioId, perms);
    }
    if (selection.includeAnnotations()) {
      List<SemanticAnnotationIO> annotations = entity.getAnnotations() == null
        ? List.of()
        : entity.getAnnotations().stream().map(SemanticAnnotationIO::new).toList();
      builder.addAnnotationsFor(ioId, annotations);
    }
    if (isCollection && selection.includeVersions()) {
      List<VersionIO> versions = versionService.getAllVersions(ioId).stream().map(VersionIO::new).toList();
      builder.addVersionsFor(ioId, versions);
    }
    if (selection.includeSubscriptions()) {
      String url = canonicalUrlFor(entity);
      List<SubscriptionIO> subs = url == null
        ? List.of()
        : subscriptionService
          .getMatchingSubscriptionsForUrl(url, RequestMethod.GET)
          .stream()
          .map(SubscriptionIO::new)
          .toList();
      builder.addSubscriptionsFor(ioId, subs);
    }
  }

  /**
   * Resolve an entity's canonical relative URL via {@link EntityUrlSynthesiser}, dispatching on
   * runtime type. Returns {@code null} if the entity (or its required parent chain) is missing
   * the data needed to synthesise a URL — in that case the caller emits an empty subscriptions
   * document, keeping the export idempotent.
   */
  private static String canonicalUrlFor(BasicEntity entity) {
    return switch (entity) {
      case Collection c -> EntityUrlSynthesiser.urlFor(c);
      case DataObject d -> EntityUrlSynthesiser.urlFor(d);
      case BasicReference r -> EntityUrlSynthesiser.urlFor(r);
      case null -> null;
      default -> null;
    };
  }

  private void fetchAndWriteDataObject(
    long collectionId,
    ExportBuilder builder,
    long dataObjectId,
    ExportSelection selection
  ) throws IOException, InvalidBodyException {
    var dataObject = dataObjectService.getDataObject(dataObjectId);
    builder.addDataObject(dataObject);
    writeEntityMetadata(builder, dataObject, selection, false);

    // TODO: Add more types, maybe improve (StrategyPattern?)
    for (BasicReference reference : dataObject.getReferences()) {
      ExportSelection.PayloadKind kind = mapKind(reference.getType());
      if (selection != null && !selection.includesKind(kind)) continue;
      String idAsString = String.valueOf(reference.getShepardId());
      if (selection != null && selection.excludesId(idAsString)) continue;
      writeEntityMetadata(builder, reference, selection, false);
      ExportSelection.PerPayloadSelection perPayload = selection == null ? null : selection.perPayloadFor(idAsString);
      switch (reference.getType()) {
        case "TimeseriesReference" -> fetchAndWriteTimeseriesReference(
          collectionId,
          dataObjectId,
          builder,
          reference.getShepardId(),
          authenticationContext.getCurrentUserName(),
          perPayload,
          selection != null && selection.isStrictPerPayload()
        );
        case "FileReference" -> fetchAndWriteFileReference(
          collectionId,
          dataObjectId,
          builder,
          reference.getShepardId(),
          perPayload,
          selection != null && selection.isStrictPerPayload()
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
    String username,
    ExportSelection.PerPayloadSelection perPayload,
    boolean strict
  ) throws IOException {
    var reference = timeseriesReferenceService.getReference(
      collectionShepardId,
      dataObjectShepardId,
      referenceId,
      null
    );
    builder.addReference(new TimeseriesReferenceIO(reference), reference.getCreatedBy());

    Set<String> requestedColumns = perPayload != null && perPayload.columns() != null
      ? new LinkedHashSet<>(perPayload.columns())
      : Collections.emptySet();
    Long startNanosOverride = null;
    Long endNanosOverride = null;
    if (perPayload != null && perPayload.timeRange() != null) {
      var tr = perPayload.timeRange();
      if (tr.start() != null) startNanosOverride = nanosFromInstant(tr.start());
      if (tr.end() != null) endNanosOverride = nanosFromInstant(tr.end());
    }

    // R2b: validate requested columns against the reference's known fields. Unknown columns are
    // silently dropped (recorded as warnings) unless strict mode flips the contract to 400.
    Set<String> effectiveFieldFilter = Collections.emptySet();
    if (!requestedColumns.isEmpty()) {
      Set<String> knownFields = new HashSet<>();
      for (var ts : reference.getReferencedTimeseriesList()) {
        var f = ts.toTimeseries().getField();
        if (f != null) knownFields.add(f);
      }
      Set<String> unknown = new LinkedHashSet<>();
      Set<String> matched = new LinkedHashSet<>();
      for (String c : requestedColumns) {
        if (knownFields.contains(c)) matched.add(c);
        else unknown.add(c);
      }
      if (!unknown.isEmpty()) {
        if (strict) {
          throw new BadRequestException(
            "Unknown column(s) for TimeseriesReference id=" + referenceId + ": " + unknown
          );
        }
        builder.addSelectionWarning(
          "TimeseriesReference id=" + referenceId + ": unknown columns skipped " + unknown
        );
      }
      effectiveFieldFilter = matched;
    }

    InputStream timeseriesPayload = null;
    try {
      timeseriesPayload = timeseriesReferenceService.exportReferencedTimeseriesByShepardId(
        collectionShepardId,
        dataObjectShepardId,
        referenceId,
        null,
        null,
        null,
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet(),
        effectiveFieldFilter,
        CsvFormat.ROW,
        startNanosOverride,
        endNanosOverride
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
    long referenceId,
    ExportSelection.PerPayloadSelection perPayload,
    boolean strict
  ) throws IOException {
    FileBundleReference reference = fileReferenceService.getReference(
      collectionShepardId,
      dataObjectShepardId,
      referenceId,
      null
    );

    builder.addReference(new FileReferenceIO(reference), reference.getCreatedBy());

    List<String> requestedOids = perPayload == null ? null : perPayload.fileOids();
    if (requestedOids == null || requestedOids.isEmpty()) {
      // No per-payload pick ⇒ keep R2 Phase 1 behaviour: fetch everything in bulk.
      List<NamedInputStream> payloads = Collections.emptyList();
      try {
        payloads = fileReferenceService.getAllPayloads(collectionShepardId, dataObjectShepardId, referenceId);
      } catch (ShepardException e) {
        Log.warn("Cannot access file payload during export");
      }
      for (var nis : payloads) {
        if (nis.getInputStream() != null) writeFilePayload(builder, nis);
      }
      return;
    }

    // R2b per-payload pick: fetch only the requested OIDs via the per-OID API. Stale OIDs (not on
    // the reference) are silently skipped unless strict.
    Set<String> knownOids = new HashSet<>();
    for (ShepardFile f : reference.getFiles()) {
      if (f.getOid() != null) knownOids.add(f.getOid());
    }
    List<String> stale = new ArrayList<>();
    List<String> hits = new ArrayList<>();
    for (String oid : requestedOids) {
      if (knownOids.contains(oid)) hits.add(oid);
      else stale.add(oid);
    }
    if (!stale.isEmpty()) {
      if (strict) {
        throw new BadRequestException("Unknown file OID(s) for FileReference id=" + referenceId + ": " + stale);
      }
      builder.addSelectionWarning("FileReference id=" + referenceId + ": stale file OIDs skipped " + stale);
    }
    for (String oid : hits) {
      try {
        var nis = fileReferenceService.getPayload(collectionShepardId, dataObjectShepardId, referenceId, oid, null);
        if (nis != null && nis.getInputStream() != null) writeFilePayload(builder, nis);
      } catch (ShepardException e) {
        Log.warnf("Cannot access file payload oid=%s during export", oid);
      }
    }
  }

  private static long nanosFromInstant(java.time.Instant instant) {
    return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
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
