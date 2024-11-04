package de.dlr.shepard.neo4Core.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.BasicReferenceIO;
import de.dlr.shepard.util.DateHelper;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.io.IOException;
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
    var crate = builder.getBuilder().build();
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

    var crate = builder.getBuilder().build();
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

    var crate = builder.getBuilder().build();
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

    var crate = builder.getBuilder().build();
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

    var crate = builder.getBuilder().build();
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
