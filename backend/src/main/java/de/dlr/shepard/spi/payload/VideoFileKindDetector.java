package de.dlr.shepard.spi.payload;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Set;

/**
 * MP4-PROMOTE-VIDEO / FILEKIND-DETECTOR-SPI — core {@link FileKindDetector}
 * that maps the common video-container extensions to the {@code "video"}
 * file-kind, so every singleton {@link
 * de.dlr.shepard.context.references.file.entities.FileReference} carrying a
 * video file is recognised and can be rendered inline (rather than treated as
 * an opaque download).
 *
 * <p><strong>Why core, not the video plugin.</strong> File-kind detection runs
 * at upload time on the core {@code :SingletonFileReference} and must fire
 * regardless of whether the {@code shepard-plugin-video} rendering plugin is
 * loaded — exactly as the sibling media kinds ({@code svdx}, {@code otvis},
 * {@code urdf}) already live in {@link BuiltinFileKindDetector} even though
 * their renderers ship as plugins. The inline {@code VideoPlayer} is likewise a
 * core frontend component. Kept as a separate bean (auto-discovered by
 * {@link FileKindDetectorRegistry} via CDI {@code @Any Instance}) so
 * {@link BuiltinFileKindDetector}'s "7 legacy families" contract stays honest
 * and the {@link Map#of} 10-entry cap is not a concern.
 *
 * <p>Recognised extensions (case-insensitive at the call site — the registry
 * lower-cases before delegating), all → {@code "video"}:
 * {@code mp4, mov, m4v, avi, mkv, webm, mpg, mpeg, wmv}.
 */
@ApplicationScoped
public class VideoFileKindDetector implements FileKindDetector {

  /** The file-kind token every claimed extension resolves to. */
  static final String VIDEO_KIND = "video";

  /**
   * Lower-case video-container extensions claimed by this detector.
   * Effectively immutable; initialised once at class-load time.
   */
  static final Set<String> VIDEO_EXTENSIONS = Set.of(
    "mp4",
    "mov",
    "m4v",
    "avi",
    "mkv",
    "webm",
    "mpg",
    "mpeg",
    "wmv"
  );

  @Override
  public Set<String> claimedExtensions() {
    return VIDEO_EXTENSIONS;
  }

  @Override
  public String fileKindFor(String extension) {
    if (extension == null) return null;
    return VIDEO_EXTENSIONS.contains(extension) ? VIDEO_KIND : null;
  }
}
