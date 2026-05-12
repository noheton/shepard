package de.dlr.shepard.context.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class ExportBuilderTest {

  @Inject
  DateHelper dateHelper;

  @Test
  public void testConstructor() throws IOException {
    var user = new User("bob");
    var collection = getCollection(user);
    var builder = new ExportBuilder(collection);
    var crate = builder.getRoCrateBuilder().build();
    var entity = crate.getDataEntityById("2.json");
    assertEquals("Collection Name", entity.getProperty("name").asText());
  }

  @Test
  public void testAddDataObject() throws IOException {
    var user = new User("bob");
    var collection = getCollection(user);
    var builder = new ExportBuilder(collection);

    var dataObject = getDataObject(user, collection);
    builder.addDataObject(dataObject);

    var crate = builder.getRoCrateBuilder().build();
    var entity = crate.getDataEntityById("3.json");
    assertEquals("DataObject Name", entity.getProperty("name").asText());
  }

  @Test
  public void testAddReference() throws IOException {
    var user = new User("bob");
    var collection = getCollection(user);
    var dataObject = getDataObject(user, collection);
    var builder = new ExportBuilder(collection);

    var reference = getReference(user, dataObject);
    builder.addReference(new BasicReferenceIO(reference), user);

    var crate = builder.getRoCrateBuilder().build();
    var entity = crate.getDataEntityById("4.json");
    assertEquals("Reference Name", entity.getProperty("name").asText());
  }

  @Test
  public void testAddPayload_bytes() throws IOException {
    var user = new User("bob");
    var collection = getCollection(user);
    var builder = new ExportBuilder(collection);

    byte[] bytes = new byte[0];
    builder.addPayload(bytes, "file", "name");

    var crate = builder.getRoCrateBuilder().build();
    var entity = crate.getEntityById("file");
    assertEquals("name", entity.getProperty("name").asText());
  }

  @Test
  public void testAddPayload_bytesPlusFormat() throws IOException {
    var user = new User("bob");
    var collection = getCollection(user);
    var builder = new ExportBuilder(collection);

    var bytes = new byte[0];
    builder.addPayload(bytes, "file", "name", "text/csv");

    var crate = builder.getRoCrateBuilder().build();
    var entity = crate.getEntityById("file");
    assertEquals("name", entity.getProperty("name").asText());
    assertEquals("text/csv", entity.getProperty("encodingFormat").asText());
  }

  @Test
  public void testBuild() throws IOException {
    var user = new User("bob");
    var collection = getCollection(user);
    var builder = new ExportBuilder(collection);

    assertNotNull(builder.build());
  }

  // U1b2 — person entity uses DisplayNameResolver and ORCID-based @id --------

  @Test
  public void personEntity_usesDisplayName_whenSet() throws IOException {
    var user = new User("bob", "Robert", "Smith", "bob@example.org");
    user.setDisplayName("Dr. Bob");
    var collection = getCollection(user);
    var builder = new ExportBuilder(collection);

    JsonNode manifest = readManifest(builder.build());
    JsonNode person = findGraphNode(manifest, "bob");
    assertNotNull(person, "person contextual entity must be in @graph");
    assertEquals("Dr. Bob", person.get("name").asText());
  }

  @Test
  public void personEntity_usesFirstLastName_whenNoDisplayName() throws IOException {
    var user = new User("bob", "Robert", "Smith", "bob@example.org");
    var collection = getCollection(user);
    var builder = new ExportBuilder(collection);

    JsonNode manifest = readManifest(builder.build());
    JsonNode person = findGraphNode(manifest, "bob");
    assertNotNull(person, "person contextual entity must be in @graph");
    assertEquals("Robert Smith", person.get("name").asText());
  }

  @Test
  public void personEntity_usesOrcidAsId_whenPresent() throws IOException {
    var user = new User("bob", "Robert", "Smith", "bob@example.org");
    user.setOrcid("0000-0002-1825-0097");
    var collection = getCollection(user);
    var builder = new ExportBuilder(collection);

    JsonNode manifest = readManifest(builder.build());
    // @id must be the ORCID URI, not the username
    JsonNode person = findGraphNode(manifest, "https://orcid.org/0000-0002-1825-0097");
    assertNotNull(person, "person entity @id must be the ORCID URI when ORCID is set");
    assertEquals("Robert Smith", person.get("name").asText());
  }

  @Test
  public void personEntity_usesUsernameAsId_whenNoOrcid() throws IOException {
    var user = new User("bob", "Robert", "Smith", "bob@example.org");
    var collection = getCollection(user);
    var builder = new ExportBuilder(collection);

    JsonNode manifest = readManifest(builder.build());
    JsonNode person = findGraphNode(manifest, "bob");
    assertNotNull(person, "person entity @id must fall back to username when no ORCID");
  }

  // helpers -----------------------------------------------------------------

  private final ObjectMapper objectMapper = new ObjectMapper();

  private JsonNode readManifest(InputStream zipStream) throws IOException {
    try (var zis = new ZipInputStream(zipStream)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (ExportConstants.ROCRATE_METADATA.equals(entry.getName())) {
          var bos = new ByteArrayOutputStream();
          byte[] buf = new byte[4096];
          int n;
          while ((n = zis.read(buf)) > 0) bos.write(buf, 0, n);
          return objectMapper.readTree(bos.toByteArray());
        }
      }
    }
    throw new AssertionError("ro-crate-metadata.json not found in ZIP");
  }

  private JsonNode findGraphNode(JsonNode tree, String id) {
    JsonNode graph = tree.get("@graph");
    if (graph == null) return null;
    for (JsonNode node : graph) {
      JsonNode nodeId = node.get("@id");
      if (nodeId != null && id.equals(nodeId.asText())) return node;
    }
    return null;
  }

  private Collection getCollection(User user) {
    var collection = new Collection() {
      {
        setId(2L);
        setShepardId(2L);
        setName("Collection Name");
        setDescription("Desc");
        setCreatedAt(dateHelper.getDate());
        setCreatedBy(user);
      }
    };
    return collection;
  }

  private DataObject getDataObject(User user, Collection collection) {
    var dataObject = new DataObject() {
      {
        setId(3L);
        setShepardId(3L);
        setName("DataObject Name");
        setCreatedAt(dateHelper.getDate());
        setUpdatedAt(dateHelper.getDate());
        setCollection(collection);
      }
    };
    return dataObject;
  }

  private BasicReference getReference(User user, DataObject dataObject) {
    var reference = new BasicReference() {
      {
        setId(4L);
        setShepardId(4L);
        setName("Reference Name");
        setCreatedAt(dateHelper.getDate());
        setCreatedBy(user);
        setDataObject(dataObject);
      }
    };
    return reference;
  }
}
