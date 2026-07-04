package de.dlr.shepard.spi.payload;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * FILEKIND-DETECTOR-SPI — CDI registry that aggregates every
 * {@link FileKindDetector} bean on the classpath and resolves
 * a lower-case file extension to a file-kind token.
 *
 * <h2>Discovery</h2>
 *
 * <p>Discovery is automatic: any {@code @ApplicationScoped} (or
 * {@code @Dependent}) bean that implements {@link FileKindDetector} is
 * injected via {@code @Any Instance<FileKindDetector>} and consulted
 * on every {@link #resolveFileKind} call. Core ships
 * {@link BuiltinFileKindDetector}; plugins contribute additional beans
 * without touching core.
 *
 * <h2>Conflict resolution</h2>
 *
 * <p>The first detector whose {@link FileKindDetector#claimedExtensions()}
 * contains the extension wins. Iteration order follows CDI bean-priority
 * ordering; the built-in detector has no explicit priority so plugins with
 * a higher {@code @Priority} can override it.
 *
 * <h2>Fail-soft</h2>
 *
 * <p>Any exception thrown by a detector's methods is caught, logged at
 * WARN level, and the registry continues to the next candidate — per
 * the CLAUDE.md "registries are fail-soft" rule.
 *
 * <h2>Test seam</h2>
 *
 * <p>Same-package unit tests may assign {@link #detectors} directly
 * (the field is package-private). Cross-package tests use the
 * {@link #FileKindDetectorRegistry(Instance)} constructor to inject a
 * fixed {@code Instance} wrapper without booting CDI.
 */
@ApplicationScoped
public class FileKindDetectorRegistry {

  /**
   * All {@link FileKindDetector} beans on the classpath.
   * Package-private to allow direct assignment in same-package unit tests.
   */
  @Inject
  @Any
  Instance<FileKindDetector> detectors;

  /** CDI no-arg constructor. */
  public FileKindDetectorRegistry() {}

  /**
   * Test-only constructor — allows cross-package tests to inject a fixed
   * {@code Instance} wrapper without booting CDI.
   *
   * @param detectors the detectors to use for {@link #resolveFileKind} lookups
   */
  public FileKindDetectorRegistry(Instance<FileKindDetector> detectors) {
    this.detectors = detectors;
  }

  /**
   * Resolve a lower-case file extension to its file-kind token.
   *
   * <p>Iterates all registered detectors in CDI-priority order. Returns
   * the first non-null result from a detector whose
   * {@link FileKindDetector#claimedExtensions()} contains {@code extension}.
   * Returns {@code null} when no detector claims the extension, preserving
   * the "absent means unknown" contract of the original switch-case.
   *
   * @param extension lower-case file extension without leading dot,
   *                  e.g. {@code "pdf"}; {@code null} or blank returns {@code null}
   * @return the file-kind token, or {@code null} when unrecognised
   */
  public String resolveFileKind(String extension) {
    if (extension == null || extension.isBlank()) {
      return null;
    }
    for (FileKindDetector detector : detectors) {
      if (detector == null) continue;
      try {
        if (detector.claimedExtensions().contains(extension)) {
          String kind = detector.fileKindFor(extension);
          if (kind != null) {
            return kind;
          }
        }
      } catch (Exception ex) {
        Log.warnf(
          ex,
          "FILEKIND-DETECTOR-SPI: FileKindDetector '%s' threw during resolveFileKind('%s') — skipping",
          detector.getClass().getName(),
          extension
        );
      }
    }
    return null;
  }
}
