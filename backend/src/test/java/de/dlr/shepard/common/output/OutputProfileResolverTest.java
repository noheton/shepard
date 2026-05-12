package de.dlr.shepard.common.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OutputProfileResolverTest {

  @Test
  void defaultsToAll() {
    var r = new OutputProfileResolver();
    assertEquals(OutputProfile.ALL, r.getProfile());
    assertFalse(r.isMetadataOnly());
    assertFalse(r.isRelationsOnly());
  }

  @Test
  void setProfilePropagates() {
    var r = new OutputProfileResolver();
    r.setProfile(OutputProfile.METADATA);
    assertEquals(OutputProfile.METADATA, r.getProfile());
    assertTrue(r.isMetadataOnly());
    assertFalse(r.isRelationsOnly());
  }

  @Test
  void nullProfileFallsBackToDefault() {
    var r = new OutputProfileResolver();
    r.setProfile(OutputProfile.RELATIONS);
    r.setProfile(null);
    assertEquals(OutputProfile.DEFAULT, r.getProfile());
  }

  @Test
  void relationsOnlyFlag() {
    var r = new OutputProfileResolver();
    r.setProfile(OutputProfile.RELATIONS);
    assertTrue(r.isRelationsOnly());
    assertFalse(r.isMetadataOnly());
  }
}
