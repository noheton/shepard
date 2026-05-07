package de.dlr.shepard.context.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class ExportBuilderSelectionTest {

  @Inject
  DateHelper dateHelper;

  private final ObjectMapper objectMapper = new ObjectMapper();

  // shared fixed timestamp so two builders constructed back-to-back produce a byte-identical manifest.
  private final java.util.Date fixedDate = new java.util.Date(1_700_000_000_000L);

  private Collection collection() {
    var user = new User("alice");
    return new Collection() {
      {
        setId(2L);
        setShepardId(2L);
        setName("Sel Crate");
        setDescription("desc");
        setCreatedAt(fixedDate);
        setCreatedBy(user);
      }
    };
  }

  private byte[] manifest(InputStream zipStream) throws IOException {
    try (var zis = new ZipInputStream(zipStream)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (ExportConstants.ROCRATE_METADATA.equals(entry.getName())) {
          var bos = new ByteArrayOutputStream();
          byte[] buf = new byte[4096];
          int n;
          while ((n = zis.read(buf)) > 0) bos.write(buf, 0, n);
          return bos.toByteArray();
        }
      }
    }
    return new byte[0];
  }

  @Test
  public void noSelection_byteIdenticalToLegacy() throws IOException {
    // Two identical builders — one with no selection, one with empty selection record (all nulls).
    var legacy = new ExportBuilder(collection());
    var emptySelection = new ExportBuilder(collection(), new ExportSelection(null, null));

    byte[] legacyManifest = manifest(legacy.build());
    byte[] emptyManifest = manifest(emptySelection.build());

    assertNotNull(legacyManifest);
    assertNotNull(emptyManifest);

    JsonNode legacyTree = objectMapper.readTree(legacyManifest);
    JsonNode emptyTree = objectMapper.readTree(emptyManifest);

    // Empty selection is considered isEmpty() ⇒ no "selection" key injected ⇒ identical manifest.
    assertEquals(legacyTree, emptyTree);
    assertFalse(findRoot(emptyTree).has("selection"));
  }

  @Test
  public void nonEmptySelection_writesSelectionBlockOnRoot() throws IOException {
    var sel = new ExportSelection(
      new ExportSelection.Payloads(Set.of(ExportSelection.PayloadKind.FileReference), List.of("abc-123")),
      new ExportSelection.Metadata(false, true, true, false, false)
    );
    var builder = new ExportBuilder(collection(), sel);
    byte[] manifestBytes = manifest(builder.build());
    JsonNode tree = objectMapper.readTree(manifestBytes);
    JsonNode root = findRoot(tree);
    assertTrue(root.has("selection"), "selection block must be present on root data entity");
    JsonNode selection = root.get("selection");
    assertEquals("FileReference", selection.get("payloads").get("include").get(0).asText());
    assertEquals("abc-123", selection.get("payloads").get("excludeIds").get(0).asText());
    assertEquals(false, selection.get("metadata").get("permissions").asBoolean());
    assertEquals(true, selection.get("metadata").get("annotations").asBoolean());
  }

  private JsonNode findRoot(JsonNode tree) {
    JsonNode graph = tree.get("@graph");
    assertNotNull(graph, "@graph must exist");
    for (JsonNode node : graph) {
      JsonNode id = node.get("@id");
      if (id != null && "./".equals(id.asText())) return node;
    }
    throw new AssertionError("root data entity (@id == './') not found in manifest");
  }
}
