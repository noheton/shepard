package de.dlr.shepard.v2.containers.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.data.file.entities.FileContainer;
import org.junit.jupiter.api.Test;

/** V2CONV-A3 — equals/hashCode + payload-builder contract for {@link ContainerV2IO}. */
class ContainerV2IOTest {

  private FileContainer container(String appId, String name) {
    var c = new FileContainer(1L);
    c.setAppId(appId);
    c.setName(name);
    return c;
  }

  @Test
  void put_returnsSelf_andStoresPayload() {
    var io = new ContainerV2IO(container("a", "n"), "file");
    var same = io.put("oid", "mongo-1").put("extra", null);
    assertTrue(same == io);
    assertEquals("mongo-1", io.getPayload().get("oid"));
    assertTrue(io.getPayload().containsKey("extra"));
  }

  @Test
  void equals_sameFields_areEqual() {
    var a = new ContainerV2IO(container("a", "n"), "file").put("oid", "x");
    var b = new ContainerV2IO(container("a", "n"), "file").put("oid", "x");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void equals_differentKind_notEqual() {
    var a = new ContainerV2IO(container("a", "n"), "file");
    var b = new ContainerV2IO(container("a", "n"), "timeseries");
    assertNotEquals(a, b);
  }

  @Test
  void equals_differentPayload_notEqual() {
    var a = new ContainerV2IO(container("a", "n"), "file").put("oid", "x");
    var b = new ContainerV2IO(container("a", "n"), "file").put("oid", "y");
    assertNotEquals(a, b);
  }

  @Test
  void equals_handlesNullAndOtherType() {
    var a = new ContainerV2IO(container("a", "n"), "file");
    assertNotEquals(a, null);
    assertNotEquals(a, "not-an-io");
    assertEquals(a, a);
  }
}
