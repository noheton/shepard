package de.dlr.shepard.data.file.thumbnail;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * TH1a — SPI for file thumbnail generation.
 *
 * <p>Implementations are CDI beans ({@code @ApplicationScoped}). The
 * {@link ThumbnailService} discovers all live implementations via
 * {@code Instance<ThumbnailProvider>} and selects the first one whose
 * {@link #supportedMimeTypes()} or {@link #supportedExtensions()} matches
 * the file being served.
 *
 * <p>Plugin modules supply additional providers on the classpath. Built-in
 * providers (raster image, plain-text) ship in-tree.
 */
public interface ThumbnailProvider {

  /** MIME types this provider handles, e.g. {@code "image/png"}. */
  Set<String> supportedMimeTypes();

  /**
   * File extensions this provider handles (lower-case, no leading dot),
   * used as a fallback when MIME type is absent or {@code "application/octet-stream"}.
   */
  Set<String> supportedExtensions();

  /**
   * Generate a thumbnail of at most {@code sizePx} on the longest side.
   *
   * @param fileBytes  raw file bytes; caller is responsible for closing the stream
   * @param filename   original filename (used for extension-based detection)
   * @param sizePx     desired longest-side pixel size; one of 64, 200, 400
   * @return PNG-encoded thumbnail bytes
   * @throws IOException if the file cannot be decoded or the thumbnail cannot be rendered
   */
  byte[] generate(InputStream fileBytes, String filename, int sizePx) throws IOException;
}
