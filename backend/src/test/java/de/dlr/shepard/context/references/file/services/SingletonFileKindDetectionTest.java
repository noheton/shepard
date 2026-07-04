package de.dlr.shepard.context.references.file.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.dlr.shepard.spi.payload.BuiltinFileKindDetector;
import de.dlr.shepard.spi.payload.FileKindDetector;
import de.dlr.shepard.spi.payload.FileKindDetectorRegistry;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V2CONV-A2 / FILEKIND-DETECTOR-SPI — unit tests for the {@code fileKind}
 * detection helper on {@link SingletonFileReferenceService}.
 *
 * <p>The helper delegates to {@link FileKindDetectorRegistry}, which in turn
 * consults all registered {@link FileKindDetector} beans. Tests here wire
 * the registry manually (no CDI) using the same {@code FixedInstance} pattern
 * as {@code FileKindDetectorRegistryTest}.
 */
class SingletonFileKindDetectionTest {

  // ─── minimal Instance<T> wrapper ─────────────────────────────────────────

  static class FixedInstance<T> implements Instance<T> {
    private final List<T> items;

    FixedInstance(List<T> items) { this.items = items; }

    @Override public Iterator<T> iterator() { return items.iterator(); }
    @Override public Stream<T> stream() { return items.stream(); }
    @Override public boolean isUnsatisfied() { return items.isEmpty(); }
    @Override public boolean isAmbiguous() { return items.size() > 1; }
    @Override public boolean isResolvable() { return !items.isEmpty(); }
    @Override public T get() { throw new UnsupportedOperationException(); }
    @Override public Instance<T> select(Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    @Override public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    @Override public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    @Override public void destroy(T instance) { throw new UnsupportedOperationException(); }
    @Override public Handle<T> getHandle() { throw new UnsupportedOperationException(); }
    @Override public Iterable<? extends Handle<T>> handles() { throw new UnsupportedOperationException(); }
    @Override public Stream<? extends Handle<T>> handlesStream() { throw new UnsupportedOperationException(); }
  }

  // ─── test setup ──────────────────────────────────────────────────────────

  private SingletonFileReferenceService svc;

  @BeforeEach
  void setUp() {
    FileKindDetectorRegistry registry = new FileKindDetectorRegistry(
      new FixedInstance<>(List.of(new BuiltinFileKindDetector())));

    svc = new SingletonFileReferenceService();
    svc.detectorRegistry = registry;
  }

  // ─── tests ────────────────────────────────────────────────────────────────

  @Test
  void detects_krl_from_both_extensions() {
    assertEquals("krl", svc.detectFileKind("program.krl", null));
    assertEquals("krl", svc.detectFileKind("MAIN.SRC", null));
  }

  @Test
  void detects_known_kinds() {
    assertEquals("svdx",     svc.detectFileKind("scene.svdx", null));
    assertEquals("otvis",    svc.detectFileKind("layup.otvis", null));
    assertEquals("urdf",     svc.detectFileKind("robot.urdf", null));
    assertEquals("xit",      svc.detectFileKind("cell.xit", null));
    assertEquals("pdf",      svc.detectFileKind("report.PDF", null));
    assertEquals("urscript", svc.detectFileKind("script.urscript", null));
    assertEquals("urscript", svc.detectFileKind("main.script", null));
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
