package de.dlr.shepard.context.export;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.labJournal.io.LabJournalEntryIO;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.version.io.VersionIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

/**
 * R2c — per-payload metadata-field redaction. Mirrors the structural style of
 * {@link ExportBuilderSelectionTest}: build a real {@link ExportBuilder}, drive an emitter, then
 * inspect the per-document and manifest JSON inside the produced ZIP. Plain JUnit (no Quarkus
 * container needed — {@link ExportBuilder} is a POJO).
 */
public class ExportRedactionTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Date fixedDate = new Date(1_700_000_000_000L);

  private Collection collection() {
    var user = new User("alice");
    return new Collection() {
      {
        setId(2L);
        setShepardId(2L);
        setName("Redaction Crate");
        setDescription("desc");
        setCreatedAt(fixedDate);
        setCreatedBy(user);
      }
    };
  }

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
    assertNotNull(graph, "@graph must exist");
    for (JsonNode node : graph) {
      JsonNode id = node.get("@id");
      if (id != null && "./".equals(id.asText())) return node;
    }
    throw new AssertionError("root data entity (@id == './') not found");
  }

  private PermissionsIO samplePermissions() {
    var io = new PermissionsIO();
    io.setOwner("alice");
    io.setPermissionType(PermissionType.Private);
    io.setReader(new String[] { "bob", "carol" });
    io.setWriter(new String[] { "dave" });
    io.setManager(new String[] { "alice" });
    io.setReaderGroupIds(new long[] { 11L, 12L });
    io.setWriterGroupIds(new long[] { 13L });
    return io;
  }

  private VersionIO sampleVersion() {
    var v = new VersionIO();
    v.setUid(UUID.randomUUID());
    v.setName("v1");
    v.setCreatedAt(fixedDate);
    v.setCreatedBy("alice");
    return v;
  }

  private SemanticAnnotationIO sampleAnnotation() {
    var a = new SemanticAnnotationIO();
    a.setId(101L);
    a.setPropertyIRI("http://example.org/p");
    a.setPropertyName("hasColour");
    a.setValueIRI("http://example.org/v");
    a.setValueName("Red");
    a.setName("hasColour-Red");
    return a;
  }

  private LabJournalEntryIO sampleLabJournal() {
    var lj = new LabJournalEntryIO();
    lj.setId(7L);
    lj.setDataObjectId(2L);
    lj.setJournalContent("Sensitive lab content");
    lj.setCreatedAt(fixedDate);
    lj.setCreatedBy("alice");
    return lj;
  }

  // ---------- per-field redaction ------------------------------------------

  @Test
  public void permissionUsername_redactsOwnerReaderWriterManager() throws IOException {
    var sel = new ExportSelection(
      null,
      new ExportSelection.Metadata(true, null, null, null, null, Set.of(ExportSelection.RedactableField.PERMISSION_USERNAME))
    );
    var builder = new ExportBuilder(collection(), sel);
    builder.addPermissionsFor(2L, samplePermissions());
    var entries = readZip(builder.build());
    JsonNode doc = objectMapper.readTree(entries.get("2-permissions.json"));
    assertEquals("[REDACTED]", doc.get("owner").asText());
    var reader = doc.get("reader");
    assertEquals(2, reader.size());
    for (JsonNode r : reader) assertEquals("[REDACTED]", r.asText());
    assertEquals("[REDACTED]", doc.get("writer").get(0).asText());
    assertEquals("[REDACTED]", doc.get("manager").get(0).asText());
    // group ids must be unchanged when only username redaction is requested.
    assertEquals(11L, doc.get("readerGroupIds").get(0).asLong());
    assertEquals(13L, doc.get("writerGroupIds").get(0).asLong());
    // permissionType must be preserved.
    assertEquals("Private", doc.get("permissionType").asText());
  }

  @Test
  public void permissionGroupIds_emptiesGroupArrays() throws IOException {
    var sel = new ExportSelection(
      null,
      new ExportSelection.Metadata(true, null, null, null, null, Set.of(ExportSelection.RedactableField.PERMISSION_GROUP_IDS))
    );
    var builder = new ExportBuilder(collection(), sel);
    builder.addPermissionsFor(2L, samplePermissions());
    var entries = readZip(builder.build());
    JsonNode doc = objectMapper.readTree(entries.get("2-permissions.json"));
    assertEquals(0, doc.get("readerGroupIds").size());
    assertEquals(0, doc.get("writerGroupIds").size());
    // usernames untouched
    assertEquals("alice", doc.get("owner").asText());
    assertEquals("bob", doc.get("reader").get(0).asText());
  }

  @Test
  public void annotationLabel_redactsPropertyNameOnly() throws IOException {
    var sel = new ExportSelection(
      null,
      new ExportSelection.Metadata(null, true, null, null, null, Set.of(ExportSelection.RedactableField.ANNOTATION_LABEL))
    );
    var builder = new ExportBuilder(collection(), sel);
    builder.addAnnotationsFor(2L, List.of(sampleAnnotation()));
    var entries = readZip(builder.build());
    JsonNode arr = objectMapper.readTree(entries.get("2-annotations.json"));
    assertEquals(1, arr.size());
    JsonNode doc = arr.get(0);
    assertEquals("[REDACTED]", doc.get("propertyName").asText());
    // valueName must be preserved when only ANNOTATION_LABEL is set.
    assertEquals("Red", doc.get("valueName").asText());
    // IRIs are not redactable (stable identifiers).
    assertEquals("http://example.org/p", doc.get("propertyIRI").asText());
    assertEquals("http://example.org/v", doc.get("valueIRI").asText());
  }

  @Test
  public void annotationValue_redactsValueNameOnly() throws IOException {
    var sel = new ExportSelection(
      null,
      new ExportSelection.Metadata(null, true, null, null, null, Set.of(ExportSelection.RedactableField.ANNOTATION_VALUE))
    );
    var builder = new ExportBuilder(collection(), sel);
    builder.addAnnotationsFor(2L, List.of(sampleAnnotation()));
    var entries = readZip(builder.build());
    JsonNode arr = objectMapper.readTree(entries.get("2-annotations.json"));
    JsonNode doc = arr.get(0);
    assertEquals("hasColour", doc.get("propertyName").asText());
    assertEquals("[REDACTED]", doc.get("valueName").asText());
  }

  @Test
  public void versionAuthor_redactsCreatedBy() throws IOException {
    var sel = new ExportSelection(
      null,
      new ExportSelection.Metadata(null, null, null, true, null, Set.of(ExportSelection.RedactableField.VERSION_AUTHOR))
    );
    var builder = new ExportBuilder(collection(), sel);
    builder.addVersionsFor(2L, List.of(sampleVersion()));
    var entries = readZip(builder.build());
    JsonNode arr = objectMapper.readTree(entries.get("2-versions.json"));
    JsonNode doc = arr.get(0);
    assertEquals("[REDACTED]", doc.get("createdBy").asText());
    // other fields preserved
    assertEquals("v1", doc.get("name").asText());
  }

  @Test
  public void labJournalContent_redactsJournalContent() throws IOException {
    // labJournal is included by default; supplying redactFields with only LAB_JOURNAL_CONTENT
    // means the entry is still emitted but with content redacted.
    var sel = new ExportSelection(
      null,
      new ExportSelection.Metadata(null, null, null, null, null, Set.of(ExportSelection.RedactableField.LAB_JOURNAL_CONTENT))
    );
    var builder = new ExportBuilder(collection(), sel);
    var alice = new User("alice");
    builder.addLabJournalEntry(sampleLabJournal(), alice);
    var entries = readZip(builder.build());
    // LabJournalEntryIO is written via createFileEntity(LabJournalEntryIO) using <id>.json
    JsonNode doc = objectMapper.readTree(entries.get("7.json"));
    assertEquals("[REDACTED]", doc.get("journalContent").asText());
    // sibling fields untouched
    assertEquals("alice", doc.get("createdBy").asText());
    assertEquals(2, doc.get("dataObjectId").asLong());
  }

  // ---------- combined ------------------------------------------------------

  @Test
  public void multipleRedactions_allApply() throws IOException {
    var redact = new HashSet<ExportSelection.RedactableField>();
    redact.add(ExportSelection.RedactableField.PERMISSION_USERNAME);
    redact.add(ExportSelection.RedactableField.ANNOTATION_LABEL);
    redact.add(ExportSelection.RedactableField.VERSION_AUTHOR);

    var sel = new ExportSelection(
      null,
      new ExportSelection.Metadata(true, true, null, true, null, redact)
    );
    var builder = new ExportBuilder(collection(), sel);
    builder.addPermissionsFor(2L, samplePermissions());
    builder.addAnnotationsFor(2L, List.of(sampleAnnotation()));
    builder.addVersionsFor(2L, List.of(sampleVersion()));
    var entries = readZip(builder.build());

    JsonNode perms = objectMapper.readTree(entries.get("2-permissions.json"));
    assertEquals("[REDACTED]", perms.get("owner").asText());

    JsonNode anns = objectMapper.readTree(entries.get("2-annotations.json"));
    assertEquals("[REDACTED]", anns.get(0).get("propertyName").asText());

    JsonNode vers = objectMapper.readTree(entries.get("2-versions.json"));
    assertEquals("[REDACTED]", vers.get(0).get("createdBy").asText());
  }

  // ---------- emptiness / regression ---------------------------------------

  @Test
  public void emptyRedactSet_isNoOp_R2dRegression() throws IOException {
    // Using the legacy 5-arg Metadata constructor — redactFields stays null. Same shape as R2d
    // tests. Nothing is redacted.
    var sel = new ExportSelection(null, new ExportSelection.Metadata(true, null, null, null, null));
    var builder = new ExportBuilder(collection(), sel);
    builder.addPermissionsFor(2L, samplePermissions());
    var entries = readZip(builder.build());
    JsonNode doc = objectMapper.readTree(entries.get("2-permissions.json"));
    assertEquals("alice", doc.get("owner").asText());
    assertEquals("bob", doc.get("reader").get(0).asText());
    // group ids preserved
    assertArrayEquals(new long[] { 11L, 12L }, longArr(doc.get("readerGroupIds")));
  }

  @Test
  public void redactionDoesNotAffectNonBundledKinds() throws IOException {
    // ANNOTATION_LABEL is set, but metadata.annotations=false ⇒ no annotations doc emitted.
    // Setting the redaction must not fail or surface anywhere.
    var sel = new ExportSelection(
      null,
      new ExportSelection.Metadata(null, false, null, null, null, Set.of(ExportSelection.RedactableField.ANNOTATION_LABEL))
    );
    var builder = new ExportBuilder(collection(), sel);
    var entries = readZip(builder.build());
    // No annotations doc emitted.
    assertFalse(entries.containsKey("2-annotations.json"));
    // Manifest still records the redactFields under selection.metadata so consumers know what
    // *would* have been redacted had the kind been bundled.
    JsonNode tree = objectMapper.readTree(entries.get(ExportConstants.ROCRATE_METADATA));
    JsonNode root = findRoot(tree);
    assertTrue(root.has("selection"));
    JsonNode redactArr = root.get("selection").get("metadata").get("redactFields");
    assertNotNull(redactArr, "redactFields must appear in the recorded selection block");
    assertEquals(1, redactArr.size());
    assertEquals("ANNOTATION_LABEL", redactArr.get(0).asText());
  }

  // ---------- manifest provenance ------------------------------------------

  @Test
  public void manifestRecordsRedactedFieldSet() throws IOException {
    var redact = new HashSet<ExportSelection.RedactableField>();
    redact.add(ExportSelection.RedactableField.PERMISSION_USERNAME);
    redact.add(ExportSelection.RedactableField.LAB_JOURNAL_CONTENT);

    var sel = new ExportSelection(null, new ExportSelection.Metadata(true, null, null, null, null, redact));
    var builder = new ExportBuilder(collection(), sel);
    builder.addPermissionsFor(2L, samplePermissions());
    var entries = readZip(builder.build());

    JsonNode tree = objectMapper.readTree(entries.get(ExportConstants.ROCRATE_METADATA));
    JsonNode root = findRoot(tree);
    JsonNode redactArr = root.get("selection").get("metadata").get("redactFields");
    assertNotNull(redactArr, "redactFields must appear in selection.metadata");
    var seen = new HashSet<String>();
    for (JsonNode v : redactArr) seen.add(v.asText());
    assertTrue(seen.contains("PERMISSION_USERNAME"));
    assertTrue(seen.contains("LAB_JOURNAL_CONTENT"));
    // The manifest must NOT also leak the replacement value (privacy invariant).
    String selectionJson = root.get("selection").toString();
    assertFalse(selectionJson.contains("[REDACTED]"), "replacement values must not leak into manifest");
  }

  @Test
  public void manifestOmitsRedactFieldsWhenNotRequested() throws IOException {
    // Legacy 5-arg constructor ⇒ redactFields null ⇒ omitted from manifest by NON_NULL.
    var sel = new ExportSelection(null, new ExportSelection.Metadata(true, null, null, null, null));
    var builder = new ExportBuilder(collection(), sel);
    builder.addPermissionsFor(2L, samplePermissions());
    var entries = readZip(builder.build());
    JsonNode tree = objectMapper.readTree(entries.get(ExportConstants.ROCRATE_METADATA));
    JsonNode root = findRoot(tree);
    JsonNode metaNode = root.get("selection").get("metadata");
    assertFalse(metaNode.has("redactFields"), "redactFields must be omitted when null/empty");
  }

  // ---------- helpers ------------------------------------------------------

  private long[] longArr(JsonNode arr) {
    long[] out = new long[arr.size()];
    for (int i = 0; i < arr.size(); i++) out[i] = arr.get(i).asLong();
    return out;
  }
}
