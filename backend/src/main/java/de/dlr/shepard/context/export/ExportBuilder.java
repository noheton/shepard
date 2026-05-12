package de.dlr.shepard.context.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.DisplayNameResolver;
import de.dlr.shepard.common.neo4j.io.AbstractDataObjectIO;
import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.common.subscription.io.SubscriptionIO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.labJournal.io.LabJournalEntryIO;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.version.io.VersionIO;
import edu.kit.datamanager.ro_crate.RoCrate.RoCrateBuilder;
import edu.kit.datamanager.ro_crate.entities.contextual.PersonEntity;
import edu.kit.datamanager.ro_crate.entities.data.FileEntity;
import edu.kit.datamanager.ro_crate.entities.data.FileEntity.FileEntityBuilder;
import io.quarkus.logging.Log;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.AccessLevel;
import lombok.Getter;

public class ExportBuilder {

  private ObjectMapper objectMapper = new ObjectMapper();

  @Getter(value = AccessLevel.PROTECTED)
  private RoCrateBuilder roCrateBuilder;

  private ByteArrayOutputStream baos;
  private ZipOutputStream zos;
  private List<String> entries;
  private final ExportSelection selection;
  // R2b: stale-OID / unknown-column notes. Surfaced under selection.warnings in the manifest.
  private final List<String> selectionWarnings = new ArrayList<>();

  public ExportBuilder(Collection collection) throws IOException {
    this(collection, null);
  }

  public ExportBuilder(Collection collection, ExportSelection selection) throws IOException {
    baos = new ByteArrayOutputStream();
    zos = new ZipOutputStream(baos);
    entries = new ArrayList<>();
    this.selection = selection;

    var roCrateName = collection.getName() + " Research Object Crate";
    var roCrateDescription = "Research Object Crate representing the shepard Collection " + collection.getName();

    roCrateBuilder = new RoCrateBuilder().addName(roCrateName).addDescription(roCrateDescription);

    var collectionEntity = createFileEntity(new CollectionIO(collection));
    roCrateBuilder.addDataEntity(collectionEntity);
    addPersonEntity(collection.getCreatedBy());
  }

  public ExportBuilder addDataObject(DataObject dataObject) throws IOException {
    var dataObjectEntity = createFileEntity(new DataObjectIO(dataObject));
    roCrateBuilder.addDataEntity(dataObjectEntity);
    addPersonEntity(dataObject.getCreatedBy());
    return this;
  }

  public ExportBuilder addReference(BasicReferenceIO reference, User author) throws IOException {
    var referenceEntity = createFileEntity(reference);
    roCrateBuilder.addDataEntity(referenceEntity);
    addPersonEntity(author);
    return this;
  }

  public ExportBuilder addLabJournalEntry(LabJournalEntryIO entry, User author) throws IOException {
    if (entry != null) redactLabJournal(entry);
    var referenceEntity = createFileEntity(entry);
    roCrateBuilder.addDataEntity(referenceEntity);
    addPersonEntity(author);
    return this;
  }

  public ExportBuilder addPermissionsFor(long entityIoId, PermissionsIO permissions) throws IOException {
    var payload = permissions != null ? permissions : new PermissionsIO();
    redactPermissions(payload);
    return addMetadataDocument(entityIoId, ExportConstants.PERMISSIONS_SUFFIX, ExportConstants.TYPE_PERMISSIONS, payload);
  }

  public ExportBuilder addVersionsFor(long entityIoId, List<VersionIO> versions) throws IOException {
    var payload = versions != null ? versions : List.<VersionIO>of();
    payload.forEach(this::redactVersion);
    return addMetadataDocument(entityIoId, ExportConstants.VERSIONS_SUFFIX, ExportConstants.TYPE_VERSIONS, payload);
  }

  public ExportBuilder addSubscriptionsFor(long entityIoId, List<SubscriptionIO> subscriptions) throws IOException {
    var payload = subscriptions != null ? subscriptions : List.<SubscriptionIO>of();
    // R2c: subscriptions have no redactable fields in this iteration (R2d2 owns the path).
    return addMetadataDocument(
      entityIoId,
      ExportConstants.SUBSCRIPTIONS_SUFFIX,
      ExportConstants.TYPE_SUBSCRIPTIONS,
      payload
    );
  }

  public ExportBuilder addAnnotationsFor(long entityIoId, List<SemanticAnnotationIO> annotations) throws IOException {
    var payload = annotations != null ? annotations : List.<SemanticAnnotationIO>of();
    payload.forEach(this::redactAnnotation);
    return addMetadataDocument(
      entityIoId,
      ExportConstants.ANNOTATIONS_SUFFIX,
      ExportConstants.TYPE_ANNOTATIONS,
      payload
    );
  }

  // -------- R2c: per-field redaction on emitted metadata IOs --------------
  // Sentinel string that replaces redacted string fields. long[] id arrays are emptied because
  // they cannot hold a sentinel string and surfacing the count alone is not a meaningful
  // privacy concession.
  private static final String REDACTED = "[REDACTED]";
  private static final long[] EMPTY_LONGS = new long[0];

  private boolean redactedField(ExportSelection.RedactableField field) {
    return selection != null && selection.isRedacted(field);
  }

  private void redactPermissions(PermissionsIO io) {
    if (io == null) return;
    if (redactedField(ExportSelection.RedactableField.PERMISSION_USERNAME)) {
      if (io.getOwner() != null) io.setOwner(REDACTED);
      io.setReader(redactStringArray(io.getReader()));
      io.setWriter(redactStringArray(io.getWriter()));
      io.setManager(redactStringArray(io.getManager()));
    }
    if (redactedField(ExportSelection.RedactableField.PERMISSION_GROUP_IDS)) {
      io.setReaderGroupIds(EMPTY_LONGS);
      io.setWriterGroupIds(EMPTY_LONGS);
    }
  }

  private void redactAnnotation(SemanticAnnotationIO io) {
    if (io == null) return;
    if (redactedField(ExportSelection.RedactableField.ANNOTATION_LABEL) && io.getPropertyName() != null) {
      io.setPropertyName(REDACTED);
    }
    if (redactedField(ExportSelection.RedactableField.ANNOTATION_VALUE) && io.getValueName() != null) {
      io.setValueName(REDACTED);
    }
  }

  private void redactVersion(VersionIO io) {
    if (io == null) return;
    if (redactedField(ExportSelection.RedactableField.VERSION_AUTHOR) && io.getCreatedBy() != null) {
      io.setCreatedBy(REDACTED);
    }
  }

  private void redactLabJournal(LabJournalEntryIO io) {
    if (io == null) return;
    if (redactedField(ExportSelection.RedactableField.LAB_JOURNAL_CONTENT) && io.getJournalContent() != null) {
      io.setJournalContent(REDACTED);
    }
  }

  private static String[] redactStringArray(String[] in) {
    if (in == null) return null;
    var out = new String[in.length];
    for (int i = 0; i < in.length; i++) out[i] = REDACTED;
    return out;
  }

  private ExportBuilder addMetadataDocument(long entityIoId, String suffix, String type, Object payload)
    throws IOException {
    var filename = entityIoId + suffix + ExportConstants.JSON_FILE_EXTENSION;
    writeEntityToZip(filename, payload);
    var fileEntityBuilder = createFileEntityBuilder(filename, entityIoId + suffix, "application/json");
    fileEntityBuilder.addProperty(ExportConstants.TYPE_PROP, type);
    roCrateBuilder.addDataEntity(fileEntityBuilder.build());
    return this;
  }

  public ExportBuilder addPayload(byte[] payload, String filename, String name) throws IOException {
    addToZip(filename, payload);
    roCrateBuilder.addDataEntity(createFileEntityBuilder(filename, name).build());
    return this;
  }

  /**
   * Records a warning emitted during selection-aware export (R2b). The warnings are surfaced
   * under {@code selection.warnings} on the root data entity in {@code ro-crate-metadata.json}.
   */
  public ExportBuilder addSelectionWarning(String warning) {
    if (warning != null && !warning.isBlank()) selectionWarnings.add(warning);
    return this;
  }

  public ExportBuilder addPayload(byte[] payload, String filename, String name, String encodingFormat)
    throws IOException {
    addToZip(filename, payload);
    roCrateBuilder.addDataEntity(createFileEntityBuilder(filename, name, encodingFormat).build());
    return this;
  }

  private void addPersonEntity(User user) {
    if (user == null) return;

    // Use ORCID as @id when available (resolvable URI per schema.org/Person); otherwise fall
    // back to the username-based identifier kept for backward-compatibility (U1a / aidocs/16).
    String personId = (user.getOrcid() != null && !user.getOrcid().isBlank())
      ? "https://orcid.org/" + user.getOrcid()
      : user.getUsername();

    var entity = new PersonEntity.PersonEntityBuilder()
      .setId(personId)
      .setEmail(user.getEmail())
      .setGivenName(user.getFirstName())
      .setFamilyName(user.getLastName())
      .build();

    // Override the name with the DisplayNameResolver's resolved display name (U1b2).
    entity.addProperty("name", DisplayNameResolver.effectiveDisplayName(user));

    roCrateBuilder.addContextualEntity(entity);
  }

  public InputStream build() throws IOException {
    var roCrate = roCrateBuilder.build();
    JsonNode tree = objectMapper.readTree(roCrate.getJsonMetadata());
    // Empty / absent selection ⇒ byte-identical legacy manifest (no "selection" key) — only
    // when there are also no R2b warnings to surface.
    boolean hasSelectionToInject = selection != null && !selection.isEmpty();
    if (hasSelectionToInject) {
      injectSelection(tree, selection, selectionWarnings);
    }
    byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(tree);
    addToZip(ExportConstants.ROCRATE_METADATA, bytes);
    zos.close();

    return new ByteArrayInputStream(baos.toByteArray());
  }

  private void injectSelection(JsonNode tree, ExportSelection sel, List<String> warnings) {
    if (!(tree instanceof ObjectNode root)) return;
    JsonNode graph = root.get("@graph");
    if (!(graph instanceof ArrayNode array)) return;
    for (JsonNode node : array) {
      if (!(node instanceof ObjectNode obj)) continue;
      JsonNode id = obj.get("@id");
      if (id != null && "./".equals(id.asText())) {
        ObjectNode selNode = (ObjectNode) objectMapper.valueToTree(sel);
        if (warnings != null && !warnings.isEmpty()) {
          ArrayNode warnArr = selNode.putArray("warnings");
          for (String w : warnings) warnArr.add(w);
        }
        obj.set("selection", selNode);
        return;
      }
    }
  }

  private FileEntity createFileEntity(AbstractDataObjectIO entity) throws IOException {
    var filename = entity.getId() + ExportConstants.JSON_FILE_EXTENSION;
    writeEntityToZip(filename, entity);
    var fileEntityBuilder = createFileEntityBuilder(entity, filename);
    fileEntityBuilder.addProperty(ExportConstants.DESCRIPTION_PROP, entity.getDescription());
    return fileEntityBuilder.build();
  }

  private FileEntity createFileEntity(LabJournalEntryIO entry) throws IOException {
    var filename = entry.getId() + ExportConstants.JSON_FILE_EXTENSION;
    writeEntityToZip(filename, entry);
    var fileEntityBuilder = createFileEntityBuilder(entry, filename);
    return fileEntityBuilder.build();
  }

  private FileEntity createFileEntity(BasicReferenceIO entity) throws IOException {
    var filename = entity.getId() + ExportConstants.JSON_FILE_EXTENSION;
    writeEntityToZip(filename, entity);
    var fileEntityBuilder = createFileEntityBuilder(entity, filename);
    fileEntityBuilder.addProperty(ExportConstants.TYPE_PROP, entity.getType());
    return fileEntityBuilder.build();
  }

  private void writeEntityToZip(String filename, Object entity) throws IOException {
    var bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(entity);
    addToZip(filename, bytes);
  }

  private static FileEntityBuilder createFileEntityBuilder(String filename, String name) {
    return new FileEntity.FileEntityBuilder().setId(filename).addProperty(ExportConstants.NAME_PROP, name);
  }

  private static FileEntityBuilder createFileEntityBuilder(String filename, String name, String encodingFormat) {
    var fileEntityBuilder = createFileEntityBuilder(filename, name);
    fileEntityBuilder.addProperty("encodingFormat", encodingFormat);
    return fileEntityBuilder;
  }

  private static FileEntityBuilder createFileEntityBuilder(LabJournalEntryIO entry, String filename) {
    var createdAt = convertToIsoDate(entry.getCreatedAt());
    var type = entry.getClass().getSimpleName().replace("IO", "");
    var fileEntityBuilder = new FileEntity.FileEntityBuilder().setId(filename);
    fileEntityBuilder.addProperty("encodingFormat", "application/json");
    fileEntityBuilder
      .addAuthor(entry.getCreatedBy())
      .addProperty(ExportConstants.CREATED_PROP, createdAt)
      .addProperty(ExportConstants.TYPE_PROP, type);
    return fileEntityBuilder;
  }

  private static FileEntityBuilder createFileEntityBuilder(BasicEntityIO entity, String filename) {
    var createdAt = convertToIsoDate(entity.getCreatedAt());
    var type = entity.getClass().getSimpleName().replace("IO", "");
    var fileEntityBuilder = createFileEntityBuilder(filename, entity.getName(), "application/json");
    fileEntityBuilder
      .addAuthor(entity.getCreatedBy())
      .addProperty(ExportConstants.CREATED_PROP, createdAt)
      .addProperty(ExportConstants.TYPE_PROP, type);
    if (entity.getUpdatedAt() != null) fileEntityBuilder.addProperty(
      ExportConstants.UPDATED_PROP,
      convertToIsoDate(entity.getUpdatedAt())
    );

    return fileEntityBuilder;
  }

  private static String convertToIsoDate(Date dateToConvert) {
    var localDateTime = LocalDateTime.ofInstant(dateToConvert.toInstant(), ZoneId.systemDefault());
    return DateTimeFormatter.ISO_DATE_TIME.format(localDateTime);
  }

  private void addToZip(String filename, byte[] bytes) throws IOException {
    if (entries.contains(filename)) {
      Log.warnf("%s already in zip file, skipping", filename);
      return;
    }

    zos.putNextEntry(new ZipEntry(filename));
    zos.write(bytes);
    zos.closeEntry();
    entries.add(filename);
  }
}
