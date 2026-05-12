package de.dlr.shepard.v2.collection.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.context.collection.entities.CollectionProperties;
import org.junit.jupiter.api.Test;

class CollectionPropertiesIOTest {

  @Test
  void fromMapsAllFields() {
    var entity = new CollectionProperties("appId-7");
    entity.setWebdavVisible(false);
    entity.setDefaultOntologyUri("http://purl.obolibrary.org/obo/ro.owl");
    entity.setUiDefaultsJson("{\"initialTab\":\"timeseries\"}");

    var io = CollectionPropertiesIO.from(entity);

    assertEquals("appId-7", io.getAppId());
    assertEquals(false, io.isWebdavVisible());
    assertEquals("http://purl.obolibrary.org/obo/ro.owl", io.getDefaultOntologyUri());
    assertEquals("{\"initialTab\":\"timeseries\"}", io.getUiDefaultsJson());
  }

  @Test
  void fromHandlesNullOptionalFields() {
    var entity = new CollectionProperties("appId-8");
    var io = CollectionPropertiesIO.from(entity);
    assertEquals("appId-8", io.getAppId());
    assertTrue(io.isWebdavVisible()); // default true
    assertNull(io.getDefaultOntologyUri());
    assertNull(io.getUiDefaultsJson());
  }
}
