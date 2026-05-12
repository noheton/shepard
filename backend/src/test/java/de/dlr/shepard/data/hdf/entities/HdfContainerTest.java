package de.dlr.shepard.data.hdf.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HdfContainerTest {

  @Test
  void defaultConstructorYieldsEmptyAttributesMap() {
    var c = new HdfContainer();
    assertNull(c.getId());
    assertNull(c.getHsdsDomain());
    assertNull(c.getDescription());
    assertEquals(0, c.getAttributes().size());
  }

  @Test
  void testIdConstructorIsForTestsOnly() {
    var c = new HdfContainer(42L);
    assertEquals(42L, c.getId());
  }

  @Test
  void equalsAndHashCodeOnFullPayload() {
    var a = new HdfContainer(1L);
    a.setName("a");
    a.setDescription("d");
    a.setHsdsDomain("/shepard/x/");
    a.setAttributes(Map.of("k", "v"));

    var b = new HdfContainer(1L);
    b.setName("a");
    b.setDescription("d");
    b.setHsdsDomain("/shepard/x/");
    b.setAttributes(Map.of("k", "v"));

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void differingHsdsDomainBreaksEquality() {
    var a = new HdfContainer(1L);
    a.setHsdsDomain("/shepard/x/");
    var b = new HdfContainer(1L);
    b.setHsdsDomain("/shepard/y/");
    assertNotEquals(a, b);
  }

  @Test
  void differingDescriptionBreaksEquality() {
    var a = new HdfContainer(1L);
    a.setDescription("alpha");
    var b = new HdfContainer(1L);
    b.setDescription("beta");
    assertNotEquals(a, b);
  }

  @Test
  void differingAttributesBreaksEquality() {
    var a = new HdfContainer(1L);
    a.setAttributes(Map.of("k", "v"));
    var b = new HdfContainer(1L);
    b.setAttributes(Map.of("k", "v2"));
    assertNotEquals(a, b);
  }
}
