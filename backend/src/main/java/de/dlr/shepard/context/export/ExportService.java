package de.dlr.shepard.context.export;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
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
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.services.SnapshotService;
import de.dlr.shepard.context.version.io.VersionIO;
import de.dlr.shepard.context.version.services.VersionService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.util.Comparator;
import java.util.stream.StreamSupport;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RequestScoped
public class ExportService {

  @Inject
  CollectionService collectionService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  LabJournalEntryService labJournalEntryService;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  PermissionsService permissionsService;

  @Inject
  VersionService versionService;

  @Inject
  SubscriptionService subscriptionService;

  @Inject
  SnapshotService snapshotService;

  /**
   * Plugin-supplied export contributors (G1c SPI). Each contributor claims
   * one or more reference types; the first match (by {@link ExportContributor#priority()})
   * handles the reference. Types not claimed by any contributor fall through to the
   * built-in {@link PayloadExportHandler} dispatch.
   */
  @Inject
  @Any
  Instance<ExportContributor> exportContributors;

  /**
   * Built-in payload-kind handlers (internal SPI). Carry the richer dispatch context
   * ({@link ExportContext}) that the plugin-facing {@link ExportContributor} signature
   * does not expose. Collected via CDI; first match by
   * {@link PayloadExportHandler#priority()} handles the reference.
   *
   * <p>The {@link BasicReferenceExportHandler} has {@code priority() = Integer.MAX_VALUE}
   * and {@code handles()} returning {@code true} for every type, so it acts as the
   * catch-all when no more-specific handler matches.
   */
  @Inject
  @Any
  Instance<PayloadExportHandler> payloadHandlers;

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

    Set<String> snapshotAppIds = resolveSnapshotFilter(selection);

    for (var dataObject : collection.getDataObjects()) {
      if (snapshotAppIds != null && !snapshotAppIds.contains(dataObject.getAppId())) {
        continue;
      }
      fetchAndWriteDataObject(collectionId, exportBuilder, dataObject.getShepardId(), selection);
    }
    return exportBuilder.build();
  }

  /**
   * Resolves the snapshot filter from the given selection.
   *
   * @param selection the export selection (may be null or have a null snapshotAppId)
   * @return {@code null} when no snapshot filter applies (include all DataObjects);
   *         an empty set when the snapshotAppId is unknown (export nothing — conservative);
   *         a non-empty set of DataObject appIds captured in the snapshot.
   */
  private Set<String> resolveSnapshotFilter(ExportSelection selection) {
    if (selection == null || selection.snapshotAppId() == null) return null;
    Snapshot snapshot = snapshotService.findByAppId(selection.snapshotAppId());
    if (snapshot == null) return Set.of(); // unknown snapshot → export nothing
    return Set.copyOf(snapshotService.listDataObjectAppIds(snapshot));
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

    for (BasicReference reference : dataObject.getReferences()) {
      ExportSelection.PayloadKind kind = mapKind(reference.getType());
      if (selection != null && !selection.includesKind(kind)) continue;
      String idAsString = String.valueOf(reference.getShepardId());
      if (selection != null && selection.excludesId(idAsString)) continue;
      writeEntityMetadata(builder, reference, selection, false);
      ExportSelection.PerPayloadSelection perPayload = selection == null ? null : selection.perPayloadFor(idAsString);
      String username = authenticationContext.getCurrentUserName();
      // Check plugin-supplied contributors first (G1c ExportContributor SPI).
      Optional<ExportContributor> contributor = StreamSupport
        .stream(exportContributors.spliterator(), false)
        .filter(c -> c.handles(reference.getType()))
        .min(Comparator.comparingInt(ExportContributor::priority));
      if (contributor.isPresent()) {
        contributor.get().contribute(builder, reference, username);
      } else {
        // Dispatch to the first matching built-in PayloadExportHandler (ordered by priority).
        // BasicReferenceExportHandler is the catch-all fallback (priority = Integer.MAX_VALUE).
        ExportContext ctx = new ExportContext(
          collectionId,
          dataObjectId,
          username,
          perPayload,
          selection != null && selection.isStrictPerPayload()
        );
        PayloadExportHandler handler = StreamSupport
          .stream(payloadHandlers.spliterator(), false)
          .filter(h -> h.handles(reference.getType()))
          .min(Comparator.comparingInt(PayloadExportHandler::priority))
          .orElseThrow(() -> new IllegalStateException(
            "No PayloadExportHandler found for type: " + reference.getType() +
            " — BasicReferenceExportHandler should always be present as catch-all"
          ));
        handler.export(builder, reference, ctx);
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

}
