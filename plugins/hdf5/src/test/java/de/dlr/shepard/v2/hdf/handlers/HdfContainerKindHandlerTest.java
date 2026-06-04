package de.dlr.shepard.v2.hdf.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PLUGIN-CONTAINER-HANDLER-HDF — unit tests for {@link HdfContainerKindHandler}.
 * Covers {@link HdfContainerKindHandler#kind()} and
 * {@link HdfContainerKindHandler#owns(de.dlr.shepard.common.neo4j.entities.BasicContainer)}.
 */
class HdfContainerKindHandlerTest {

  HdfContainerKindHandler handler;

  @BeforeEach
  void setUp() {
    handler = new HdfContainerKindHandler();
  }

  @Test
  void kindIsHdf() {
    assertEquals("hdf", handler.kind());
  }

  @Test
  void ownsHdfContainer() {
    assertTrue(handler.owns(new HdfContainer()));
  }

  @Test
  void doesNotOwnOtherContainer() {
    assertFalse(handler.owns(new FileContainer()));
  }
}
