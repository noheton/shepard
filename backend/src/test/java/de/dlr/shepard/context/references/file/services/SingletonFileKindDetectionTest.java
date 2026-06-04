package de.dlr.shepard.context.references.file.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * V2CONV-A2 — unit tests for the {@code fileKind} detection helper on
 * {@link SingletonFileReferenceService}. The helper is package-private so the
 * test lives in the same package; it needs no injected collaborators.
 */
class SingletonFileKindDetectionTest {

  private final SingletonFileReferenceService svc = new SingletonFileReferenceService();

  @Test
  void detects_krl_from_both_extensions() {
    assertEquals("krl", svc.detectFileKind("program.krl", null));
    assertEquals("krl", svc.detectFileKind("MAIN.SRC", null));
  }

  @Test
  void detects_known_kinds() {
    assertEquals("svdx", svc.detectFileKind("scene.svdx", null));
    assertEquals("otvis", svc.detectFileKind("layup.otvis", null));
    assertEquals("urdf", svc.detectFileKind("robot.urdf", null));
    assertEquals("xit", svc.detectFileKind("cell.xit", null));
    assertEquals("pdf", svc.detectFileKind("report.PDF", null));
  }

  @Test
  void returns_null_for_unknown_or_extensionless() {
    assertNull(svc.detectFileKind("data.bin", null));
    assertNull(svc.detectFileKind("noextension", null));
    assertNull(svc.detectFileKind("trailingdot.", null));
    assertNull(svc.detectFileKind("", null));
    assertNull(svc.detectFileKind(null, null));
  }
}
