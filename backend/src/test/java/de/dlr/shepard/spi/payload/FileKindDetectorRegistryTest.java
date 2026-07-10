package de.dlr.shepard.spi.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.Test;

/**
 * FILEKIND-DETECTOR-SPI — unit tests for {@link FileKindDetectorRegistry}
 * and {@link BuiltinFileKindDetector}.
 *
 * <p>Uses a lightweight {@code FixedInstance} helper (inner class) to
 * inject a fixed list of {@link FileKindDetector}s without booting CDI.
 */
class FileKindDetectorRegistryTest {

  // ─── helper: FixedInstance ────────────────────────────────────────────────

  /**
   * Minimal {@link Instance}{@code <T>} wrapper around a fixed {@link List}.
   * Only {@link #iterator()} and {@link #stream()} are implemented —
   * all other methods throw {@link UnsupportedOperationException} (none
   * are called by {@link FileKindDetectorRegistry#resolveFileKind}).
   */
  static class FixedInstance<T> implements Instance<T> {

    private final List<T> items;

    FixedInstance(List<T> items) {
      this.items = items;
    }

    @Override
    public Iterator<T> iterator() {
      return items.iterator();
    }

    @Override
    public Stream<T> stream() {
      return items.stream();
    }

    @Override
    public boolean isUnsatisfied() {
      return items.isEmpty();
    }

    @Override
    public boolean isAmbiguous() {
      return items.size() > 1;
    }

    // ── all remaining Instance methods are unused by the registry ───────────

    @Override
    public T get() { throw new UnsupportedOperationException(); }

    @Override
    public Instance<T> select(Annotation... qualifiers) { throw new UnsupportedOperationException(); }

    @Override
    public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) { throw new UnsupportedOperationException(); }

    @Override
    public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) { throw new UnsupportedOperationException(); }

    @Override
    public boolean isResolvable() { return !items.isEmpty(); }

    @Override
    public void destroy(T instance) { throw new UnsupportedOperationException(); }

    @Override
    public Handle<T> getHandle() { throw new UnsupportedOperationException(); }

    @Override
    public Iterable<? extends Handle<T>> handles() { throw new UnsupportedOperationException(); }

    @Override
    public Stream<? extends Handle<T>> handlesStream() { throw new UnsupportedOperationException(); }
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private FileKindDetectorRegistry registryWith(FileKindDetector... detectors) {
    return new FileKindDetectorRegistry(new FixedInstance<>(List.of(detectors)));
  }

  // ─── BuiltinFileKindDetector tests ────────────────────────────────────────

  @Test
  void builtin_coversKrlAndSrc() {
    FileKindDetectorRegistry reg = registryWith(new BuiltinFileKindDetector());
    assertEquals("krl", reg.resolveFileKind("krl"));
    assertEquals("krl", reg.resolveFileKind("src"));
  }

  @Test
  void builtin_coversUrscriptAndScript() {
    FileKindDetectorRegistry reg = registryWith(new BuiltinFileKindDetector());
    assertEquals("urscript", reg.resolveFileKind("urscript"));
    assertEquals("urscript", reg.resolveFileKind("script"));
  }

  @Test
  void builtin_coversSingleExtensionKinds() {
    FileKindDetectorRegistry reg = registryWith(new BuiltinFileKindDetector());
    assertEquals("svdx",  reg.resolveFileKind("svdx"));
    assertEquals("otvis", reg.resolveFileKind("otvis"));
    assertEquals("urdf",  reg.resolveFileKind("urdf"));
    assertEquals("xit",   reg.resolveFileKind("xit"));
    assertEquals("pdf",   reg.resolveFileKind("pdf"));
  }

  // ─── VideoFileKindDetector tests (MP4-PROMOTE-VIDEO) ──────────────────────

  @Test
  void video_claimsAllCommonVideoExtensions() {
    FileKindDetectorRegistry reg = registryWith(new VideoFileKindDetector());
    for (String ext : new String[] {
      "mp4", "mov", "m4v", "avi", "mkv", "webm", "mpg", "mpeg", "wmv"
    }) {
      assertEquals("video", reg.resolveFileKind(ext), "extension: " + ext);
    }
  }

  @Test
  void video_ignoresNonVideoExtensions() {
    VideoFileKindDetector detector = new VideoFileKindDetector();
    assertNull(detector.fileKindFor("pdf"));
    assertNull(detector.fileKindFor("txt"));
    assertNull(detector.fileKindFor("urdf"));
    assertNull(detector.fileKindFor(null));
    FileKindDetectorRegistry reg = registryWith(detector);
    assertNull(reg.resolveFileKind("pdf"));
    assertNull(reg.resolveFileKind("bin"));
  }

  @Test
  void video_coexistsWithBuiltin() {
    FileKindDetectorRegistry reg = registryWith(
      new BuiltinFileKindDetector(),
      new VideoFileKindDetector()
    );
    // video kinds resolve
    assertEquals("video", reg.resolveFileKind("mp4"));
    assertEquals("video", reg.resolveFileKind("webm"));
    // builtin kinds still resolve
    assertEquals("pdf", reg.resolveFileKind("pdf"));
    assertEquals("urdf", reg.resolveFileKind("urdf"));
  }

  // ─── ImageFileKindDetector tests (TIFF-PREVIEW-SUPPORT) ───────────────────

  @Test
  void image_claimsAllCommonImageExtensions() {
    FileKindDetectorRegistry reg = registryWith(new ImageFileKindDetector());
    for (String ext : new String[] {
      "png", "jpg", "jpeg", "gif", "bmp", "webp", "tif", "tiff"
    }) {
      assertEquals("image", reg.resolveFileKind(ext), "extension: " + ext);
    }
  }

  @Test
  void image_ignoresNonImageExtensions() {
    ImageFileKindDetector detector = new ImageFileKindDetector();
    assertNull(detector.fileKindFor("pdf"));
    assertNull(detector.fileKindFor("mp4"));
    assertNull(detector.fileKindFor("heic"));
    assertNull(detector.fileKindFor(null));
    FileKindDetectorRegistry reg = registryWith(detector);
    assertNull(reg.resolveFileKind("pdf"));
    assertNull(reg.resolveFileKind("bin"));
  }

  @Test
  void image_coexistsWithBuiltinAndVideo() {
    FileKindDetectorRegistry reg = registryWith(
      new BuiltinFileKindDetector(),
      new VideoFileKindDetector(),
      new ImageFileKindDetector()
    );
    // image kinds resolve
    assertEquals("image", reg.resolveFileKind("tiff"));
    assertEquals("image", reg.resolveFileKind("png"));
    // video + builtin kinds still resolve — no cross-detector collision
    assertEquals("video", reg.resolveFileKind("mp4"));
    assertEquals("pdf", reg.resolveFileKind("pdf"));
    assertEquals("urdf", reg.resolveFileKind("urdf"));
  }

  @Test
  void image_tifAndTiffBothResolve() {
    // TPS tapelaying evaluation files and stringer-welding camera frames use
    // both spellings; both must resolve to the same "image" kind.
    FileKindDetectorRegistry reg = registryWith(new ImageFileKindDetector());
    assertEquals("image", reg.resolveFileKind("tif"));
    assertEquals("image", reg.resolveFileKind("tiff"));
  }

  // ─── null / unknown extension ─────────────────────────────────────────────

  @Test
  void returnsNullForUnknownExtension() {
    FileKindDetectorRegistry reg = registryWith(new BuiltinFileKindDetector());
    assertNull(reg.resolveFileKind("bin"));
    assertNull(reg.resolveFileKind("exe"));
    assertNull(reg.resolveFileKind("xyz123"));
  }

  @Test
  void returnsNullForNullOrBlankExtension() {
    FileKindDetectorRegistry reg = registryWith(new BuiltinFileKindDetector());
    assertNull(reg.resolveFileKind(null));
    assertNull(reg.resolveFileKind(""));
    assertNull(reg.resolveFileKind("   "));
  }

  // ─── custom detector ─────────────────────────────────────────────────────

  /**
   * A trivial custom detector that claims {@code "myext"} → {@code "myKind"}.
   */
  static class CustomDetector implements FileKindDetector {
    @Override
    public Set<String> claimedExtensions() {
      return Set.of("myext");
    }

    @Override
    public String fileKindFor(String extension) {
      return "myext".equals(extension) ? "myKind" : null;
    }
  }

  @Test
  void customDetectorResolvesItsOwnExtension() {
    FileKindDetectorRegistry reg = registryWith(new BuiltinFileKindDetector(), new CustomDetector());
    assertEquals("myKind", reg.resolveFileKind("myext"));
    // built-in still works
    assertEquals("pdf", reg.resolveFileKind("pdf"));
  }

  @Test
  void customDetectorBeforeBuiltinWins() {
    // A custom detector that overrides "pdf" → "customPdf"
    FileKindDetector overrider = new FileKindDetector() {
      @Override
      public Set<String> claimedExtensions() { return Set.of("pdf"); }

      @Override
      public String fileKindFor(String extension) {
        return "pdf".equals(extension) ? "customPdf" : null;
      }
    };
    // Overrider listed first — it should win over the builtin
    FileKindDetectorRegistry reg = registryWith(overrider, new BuiltinFileKindDetector());
    assertEquals("customPdf", reg.resolveFileKind("pdf"));
  }

  // ─── fail-soft: detector that throws ─────────────────────────────────────

  @Test
  void failSoft_detectorThatThrowsDoesNotPropagateToCalller() {
    FileKindDetector explosive = new FileKindDetector() {
      @Override
      public Set<String> claimedExtensions() {
        throw new RuntimeException("boom from claimedExtensions");
      }

      @Override
      public String fileKindFor(String extension) {
        throw new RuntimeException("boom from fileKindFor");
      }
    };
    FileKindDetectorRegistry reg = registryWith(explosive, new BuiltinFileKindDetector());
    // The explosive detector throws from claimedExtensions(); the registry must
    // catch it, log a warning, and fall through to the builtin.
    assertEquals("pdf", reg.resolveFileKind("pdf"));
    // Unknown extension still returns null gracefully.
    assertNull(reg.resolveFileKind("zzz"));
  }
}
