package de.dlr.shepard.v2.admin.hdf.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HdfRebuildAclsResultIOTest {

  @Test
  void noArgsConstructor_yieldsEmptyErrors() {
    var io = new HdfRebuildAclsResultIO();
    assertEquals(0, io.getContainersProcessed());
    assertEquals(0, io.getContainersSynced());
    assertNotNull(io.getErrors());
    assertTrue(io.getErrors().isEmpty());
  }

  @Test
  void allArgsConstructor_setsAllFields() {
    var errs = List.of(new HdfRebuildAclsResultIO.Error("app-1", "boom"));
    var io = new HdfRebuildAclsResultIO(5, 3, errs);
    assertEquals(5, io.getContainersProcessed());
    assertEquals(3, io.getContainersSynced());
    assertEquals(1, io.getErrors().size());
    assertEquals("app-1", io.getErrors().get(0).getContainerAppId());
    assertEquals("boom", io.getErrors().get(0).getReason());
  }

  @Test
  void allArgsConstructor_defensivelyCopiesErrors() {
    var src = new ArrayList<HdfRebuildAclsResultIO.Error>();
    src.add(new HdfRebuildAclsResultIO.Error("app-1", "x"));
    var io = new HdfRebuildAclsResultIO(1, 0, src);
    assertNotSame(src, io.getErrors());
    src.clear();
    assertEquals(1, io.getErrors().size());
  }

  @Test
  void allArgsConstructor_acceptsNullErrors() {
    var io = new HdfRebuildAclsResultIO(0, 0, null);
    assertNotNull(io.getErrors());
    assertTrue(io.getErrors().isEmpty());
  }

  @Test
  void setters_roundTrip() {
    var io = new HdfRebuildAclsResultIO();
    io.setContainersProcessed(7);
    io.setContainersSynced(6);
    io.setErrors(List.of(new HdfRebuildAclsResultIO.Error("app-2", "nope")));
    assertEquals(7, io.getContainersProcessed());
    assertEquals(6, io.getContainersSynced());
    assertEquals(1, io.getErrors().size());
  }

  @Test
  void setErrors_nullYieldsEmpty() {
    var io = new HdfRebuildAclsResultIO(1, 1, List.of());
    io.setErrors(null);
    assertNotNull(io.getErrors());
    assertTrue(io.getErrors().isEmpty());
  }

  @Test
  void errorNoArgsConstructor_nullFields() {
    var e = new HdfRebuildAclsResultIO.Error();
    org.junit.jupiter.api.Assertions.assertNull(e.getContainerAppId());
    org.junit.jupiter.api.Assertions.assertNull(e.getReason());
  }

  @Test
  void errorSetters_roundTrip() {
    var e = new HdfRebuildAclsResultIO.Error();
    e.setContainerAppId("app-9");
    e.setReason("hsds 503");
    assertEquals("app-9", e.getContainerAppId());
    assertEquals("hsds 503", e.getReason());
  }
}
