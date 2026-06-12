package de.dlr.shepard.v2.references.io;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.v2.bundle.io.FileBundleReferenceIO;
import de.dlr.shepard.v2.file.io.FileReferenceV2IO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-BASICREF-DATAOBJECTID — verifies that the three /v2/ reference IO
 * classes suppress {@code id} (Neo4j node-id) and {@code dataObjectId}
 * (numeric parent-DataObject FK) from their Jackson-serialised wire shapes.
 *
 * All three extend {@link BasicReferenceV2IO} which carries
 * {@code @JsonIgnoreProperties({"id","dataObjectId"})}; this test pins
 * the suppression contract so a future parent-class refactor can't silently
 * re-expose numeric ids on the /v2/ surface.
 */
class BasicReferenceV2IOTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private DataObject parent;

  @BeforeEach
  void setUp() {
    parent = new DataObject(42L);
    parent.setAppId("do-app-id");
    parent.setShepardId(101L);
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private FileReference singletonRef() {
    var ref = new FileReference(7L);
    ref.setAppId("ref-app-id");
    ref.setName("doc");
    ref.setDataObject(parent);
    return ref;
  }

  private FileBundleReference bundleRef() {
    var ref = new FileBundleReference(8L);
    ref.setAppId("bundle-app-id");
    ref.setName("bundle");
    ref.setDataObject(parent);
    return ref;
  }

  // ─── ReferenceV2IO ────────────────────────────────────────────────────────

  @Test
  void referenceV2IO_doesNotSerialise_id() throws Exception {
    var io = new ReferenceV2IO(singletonRef(), "file");
    String json = mapper.writeValueAsString(io);
    assertFalse(json.contains("\"id\""), "\"id\" must not appear in /v2/ wire: " + json);
  }

  @Test
  void referenceV2IO_doesNotSerialise_dataObjectId() throws Exception {
    var io = new ReferenceV2IO(singletonRef(), "file");
    String json = mapper.writeValueAsString(io);
    assertFalse(json.contains("\"dataObjectId\""), "\"dataObjectId\" must not appear in /v2/ wire: " + json);
  }

  @Test
  void referenceV2IO_serialises_appId() throws Exception {
    var io = new ReferenceV2IO(singletonRef(), "file");
    String json = mapper.writeValueAsString(io);
    assertTrue(json.contains("\"appId\""), "appId must be present: " + json);
    assertTrue(json.contains("ref-app-id"), "appId value must be the UUID: " + json);
  }

  // ─── FileReferenceV2IO ────────────────────────────────────────────────────

  @Test
  void fileReferenceV2IO_doesNotSerialise_id() throws Exception {
    var io = new FileReferenceV2IO(singletonRef());
    String json = mapper.writeValueAsString(io);
    assertFalse(json.contains("\"id\""), "\"id\" must not appear in /v2/ wire: " + json);
  }

  @Test
  void fileReferenceV2IO_doesNotSerialise_dataObjectId() throws Exception {
    var io = new FileReferenceV2IO(singletonRef());
    String json = mapper.writeValueAsString(io);
    assertFalse(json.contains("\"dataObjectId\""), "\"dataObjectId\" must not appear: " + json);
  }

  @Test
  void fileReferenceV2IO_serialises_appId() throws Exception {
    var io = new FileReferenceV2IO(singletonRef());
    String json = mapper.writeValueAsString(io);
    assertTrue(json.contains("ref-app-id"), "appId value must be present: " + json);
  }

  // ─── FileBundleReferenceIO ────────────────────────────────────────────────

  @Test
  void fileBundleReferenceIO_doesNotSerialise_id() throws Exception {
    var io = new FileBundleReferenceIO(bundleRef());
    String json = mapper.writeValueAsString(io);
    assertFalse(json.contains("\"id\""), "\"id\" must not appear in /v2/ wire: " + json);
  }

  @Test
  void fileBundleReferenceIO_doesNotSerialise_dataObjectId() throws Exception {
    var io = new FileBundleReferenceIO(bundleRef());
    String json = mapper.writeValueAsString(io);
    assertFalse(json.contains("\"dataObjectId\""), "\"dataObjectId\" must not appear: " + json);
  }

  @Test
  void fileBundleReferenceIO_serialises_appId() throws Exception {
    var io = new FileBundleReferenceIO(bundleRef());
    String json = mapper.writeValueAsString(io);
    assertTrue(json.contains("bundle-app-id"), "appId value must be present: " + json);
    assertNotNull(io.getGroups());
  }
}
