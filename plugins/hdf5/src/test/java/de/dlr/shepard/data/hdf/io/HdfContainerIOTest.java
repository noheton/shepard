package de.dlr.shepard.data.hdf.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.data.hdf.entities.HdfContainer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HdfContainerIOTest {

  @Test
  void fromEntityMirrorsAllAdditiveFields() {
    var entity = new HdfContainer(7L);
    entity.setAppId("app-id-7");
    entity.setName("primary HDF");
    entity.setDescription("temp data");
    entity.setHsdsDomain("/shepard/app-id-7/");
    entity.setAttributes(Map.of("project", "rocket"));

    var io = new HdfContainerIO(entity);

    assertEquals("app-id-7", io.getAppId());
    assertEquals("primary HDF", io.getName());
    assertEquals("temp data", io.getDescription());
    assertEquals("/shepard/app-id-7/", io.getHsdsDomain());
    assertEquals(Map.of("project", "rocket"), io.getAttributes());
  }

  @Test
  void fromEntityWithNullAttributesYieldsEmptyMap() {
    var entity = new HdfContainer(7L);
    entity.setAttributes(null);
    var io = new HdfContainerIO(entity);
    assertNotNull(io.getAttributes());
    assertTrue(io.getAttributes().isEmpty());
  }

  @Test
  void fromEntitiesListEmptyForNullInput() {
    assertEquals(List.of(), HdfContainerIO.fromEntities(null));
  }

  @Test
  void fromEntitiesListMapsAll() {
    var a = new HdfContainer(1L);
    a.setAppId("a");
    var b = new HdfContainer(2L);
    b.setAppId("b");
    var ios = HdfContainerIO.fromEntities(List.of(a, b));
    assertEquals(2, ios.size());
    assertEquals("a", ios.get(0).getAppId());
    assertEquals("b", ios.get(1).getAppId());
  }

  @Test
  void noArgsCtorYieldsEmptyShape() {
    var io = new HdfContainerIO();
    assertNull(io.getAppId());
    assertNull(io.getHsdsDomain());
    assertNotNull(io.getAttributes());
    assertTrue(io.getAttributes().isEmpty());
  }
}
