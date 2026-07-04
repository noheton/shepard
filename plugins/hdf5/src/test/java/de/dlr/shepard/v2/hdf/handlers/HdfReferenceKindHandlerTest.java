package de.dlr.shepard.v2.hdf.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.context.references.uri.entities.URIReference;
import de.dlr.shepard.data.hdf.entities.HdfReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PLUGIN-REF-HANDLER-HDF — unit tests for {@link HdfReferenceKindHandler}.
 * Covers {@link HdfReferenceKindHandler#kind()} and
 * {@link HdfReferenceKindHandler#owns(de.dlr.shepard.context.references.basicreference.entities.BasicReference)}.
 */
class HdfReferenceKindHandlerTest {

  HdfReferenceKindHandler handler;

  @BeforeEach
  void setUp() {
    handler = new HdfReferenceKindHandler();
  }

  @Test
  void kindIsHdf() {
    assertEquals("hdf", handler.kind());
  }

  @Test
  void ownsHdfReference() {
    assertTrue(handler.owns(new HdfReference()));
  }

  @Test
  void doesNotOwnOtherReference() {
    assertFalse(handler.owns(new URIReference()));
  }
}
