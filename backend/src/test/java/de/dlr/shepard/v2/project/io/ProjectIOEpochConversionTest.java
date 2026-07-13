package de.dlr.shepard.v2.project.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for APISIMP-MULTI-IO-EPOCH-MS-TO-ISO: ProjectIO and SubCollectionItemIO
 * lastActivity field is a String (ISO 8601) not a Long (epoch millis).
 */
class ProjectIOEpochConversionTest {

  @Test
  void projectIoLastActivityAcceptsIso8601String() {
    ProjectIO io = new ProjectIO();
    io.setLastActivity("2025-07-01T00:00:00Z");
    assertEquals("2025-07-01T00:00:00Z", io.getLastActivity());
  }

  @Test
  void projectIoLastActivityDefaultsToNull() {
    ProjectIO io = new ProjectIO();
    assertNull(io.getLastActivity());
  }

  @Test
  void subCollectionItemIoLastActivityAcceptsIso8601String() {
    SubCollectionItemIO io = new SubCollectionItemIO();
    io.setLastActivity("2025-07-02T12:30:00Z");
    assertEquals("2025-07-02T12:30:00Z", io.getLastActivity());
  }

  @Test
  void subCollectionItemIoLastActivityDefaultsToNull() {
    SubCollectionItemIO io = new SubCollectionItemIO();
    assertNull(io.getLastActivity());
  }
}
