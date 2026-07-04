package de.dlr.shepard.spi.payload;

import java.util.Set;

/**
 * FILEKIND-DETECTOR-SPI — pluggable SPI for contributing file-kind
 * mappings without touching core.
 *
 * <p>Any CDI bean (core or plugin) that implements this interface is
 * automatically picked up by {@link FileKindDetectorRegistry} at runtime.
 * Plugins add their own file-kind mappings by shipping an
 * {@code @ApplicationScoped} implementation of this interface.
 *
 * <h2>Contract</h2>
 *
 * <ul>
 *   <li>{@link #claimedExtensions()} declares the lower-case extensions
 *       this detector will handle (e.g. {@code "krl"}, {@code "src"}).</li>
 *   <li>{@link #fileKindFor(String)} returns the file-kind token for a
 *       given lower-case extension, or {@code null} when the extension is
 *       not handled (the registry will try the next detector).</li>
 * </ul>
 *
 * <p>Extensions are always lower-case when passed to {@link #fileKindFor};
 * implementations need not normalise them again.
 *
 * <p>A single detector may claim multiple extensions that resolve to
 * <em>different</em> file-kind tokens (e.g. {@code "krl"} and {@code "src"}
 * both → {@code "krl"}).
 *
 * <p>See {@link BuiltinFileKindDetector} for the canonical in-tree example.
 */
public interface FileKindDetector {

  /**
   * The lower-case file extensions this detector handles.
   *
   * <p>The set must be stable across calls and must not return
   * null or contain null entries. An empty set is valid (the
   * detector registers but remains dormant).
   *
   * @return immutable set of claimed lower-case extensions
   */
  Set<String> claimedExtensions();

  /**
   * Return the file-kind token for a given lower-case extension.
   *
   * <p>Implementations should return {@code null} (not throw) when the
   * extension is not recognised — the registry will continue to the
   * next candidate. Throwing from this method causes the registry to log
   * a warning and skip this detector for the current lookup (fail-soft).
   *
   * @param extension lower-case extension without leading dot, e.g. {@code "pdf"}
   * @return the file-kind token, or {@code null} when not handled
   */
  String fileKindFor(String extension);
}
