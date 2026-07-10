package de.dlr.shepard.spi.payload;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

/**
 * TIFF-PREVIEW-SUPPORT / FILEKIND-DETECTOR-SPI — core {@link FileKindDetector}
 * that maps common raster-image extensions to the {@code "image"} file-kind,
 * so a singleton {@link de.dlr.shepard.context.references.file.entities.FileReference}
 * carrying an image is recognised and gets the inline quick-look preview on
 * the file-reference detail page (mirrors {@link VideoFileKindDetector} for
 * {@code "video"}).
 *
 * <p><strong>Why this didn't exist before.</strong> Before TIFF-PREVIEW-SUPPORT
 * (2026-07-10), no {@link FileKindDetector} claimed any of these extensions,
 * so {@code fileKind === "image"} — the gate the file-reference detail page's
 * {@code inlineImageUrl} computed already checked — was permanently
 * unreachable dead code for singleton uploads: a plain {@code .png} upload
 * got {@code fileKind = null} and fell through to the generic "Open as…"
 * picker instead of the intended inline preview. This detector closes that
 * gap for the formats {@link de.dlr.shepard.data.file.thumbnail.RasterImageThumbnailProvider}
 * can already thumbnail, and simultaneously makes {@code .tif}/{@code .tiff}
 * recognised — the full-size viewer requests those through the
 * {@code ?rendition=png} transcode on {@code GET /v2/references/{appId}/content}
 * (see {@code FileReferenceKindHandler}) since browsers cannot render TIFF
 * natively.
 *
 * <p>Scoped to formats a browser can render inline once TIFF gets its PNG
 * rendition: {@code png, jpg, jpeg, gif, bmp, webp, tif, tiff}. Formats that
 * need further work before they're genuinely inline-viewable (heic, heif,
 * avif, ico, svg) are intentionally excluded pending a follow-up — see
 * {@code IMAGE-FILEKIND-EXTEND} in {@code aidocs/16}.
 */
@ApplicationScoped
public class ImageFileKindDetector implements FileKindDetector {

  /** The file-kind token every claimed extension resolves to. */
  static final String IMAGE_KIND = "image";

  /**
   * Lower-case raster-image extensions claimed by this detector.
   * Effectively immutable; initialised once at class-load time.
   */
  static final Set<String> IMAGE_EXTENSIONS = Set.of(
    "png",
    "jpg",
    "jpeg",
    "gif",
    "bmp",
    "webp",
    "tif",
    "tiff"
  );

  @Override
  public Set<String> claimedExtensions() {
    return IMAGE_EXTENSIONS;
  }

  @Override
  public String fileKindFor(String extension) {
    if (extension == null) return null;
    return IMAGE_EXTENSIONS.contains(extension) ? IMAGE_KIND : null;
  }
}
