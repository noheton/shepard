package de.dlr.shepard.spi.payload;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Set;

/**
 * FILEKIND-DETECTOR-SPI — built-in {@link FileKindDetector} that covers the
 * 7 extension families recognised by Shepard core before the SPI existed.
 *
 * <p>This bean is the in-tree migration of the private switch-case in
 * {@code SingletonFileReferenceService.detectFileKind} (V2CONV-A2). It is
 * automatically discovered by {@link FileKindDetectorRegistry} via CDI
 * {@code @Any Instance<FileKindDetector>}.
 *
 * <p>Recognised mappings (case-insensitive at the call site — the registry
 * normalises to lower-case before delegating):
 * <ul>
 *   <li>{@code krl}, {@code src} → {@code "krl"}</li>
 *   <li>{@code svdx} → {@code "svdx"}</li>
 *   <li>{@code otvis} → {@code "otvis"}</li>
 *   <li>{@code urdf} → {@code "urdf"}</li>
 *   <li>{@code xit} → {@code "xit"}</li>
 *   <li>{@code pdf} → {@code "pdf"}</li>
 *   <li>{@code urscript}, {@code script} → {@code "urscript"}</li>
 * </ul>
 */
@ApplicationScoped
public class BuiltinFileKindDetector implements FileKindDetector {

  /**
   * Extension → file-kind token map covering all built-in kinds.
   * Initialised once at class-load time; effectively immutable.
   */
  static final Map<String, String> EXT_TO_KIND = Map.of(
    "krl", "krl",
    "src", "krl",
    "svdx", "svdx",
    "otvis", "otvis",
    "urdf", "urdf",
    "xit", "xit",
    "pdf", "pdf",
    "urscript", "urscript",
    "script", "urscript"
  );

  @Override
  public Set<String> claimedExtensions() {
    return EXT_TO_KIND.keySet();
  }

  @Override
  public String fileKindFor(String extension) {
    if (extension == null) return null;
    return EXT_TO_KIND.get(extension);
  }
}
