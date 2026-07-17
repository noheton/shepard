package de.dlr.shepard.common.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * TIFF-PREVIEW-SUPPORT — shared, OOM-guarded raster decode used by both the
 * thumbnail SPI ({@link de.dlr.shepard.data.file.thumbnail.RasterImageThumbnailProvider})
 * and the singleton FileReference PNG rendition transcode
 * ({@code de.dlr.shepard.v2.references.handlers.FileReferenceKindHandler}).
 *
 * <p>Wraps {@code ImageIO.read} with an upfront dimension check via the
 * resolved {@link ImageReader} so a maliciously-crafted or absurdly large
 * source image (TIFF once the TwelveMonkeys {@code imageio-tiff} plugin is
 * on the classpath, or any other ImageIO-registered format) is rejected
 * before Java allocates the full decoded raster — per the CLAUDE.md
 * "fail-soft" + "never OOM the shared backend pool" posture.
 *
 * <p>{@link #readCappedAsRgb8} additionally normalises the decoded image to
 * 8-bit {@code TYPE_INT_RGB} via {@link Graphics2D}, so a 16-bit / grayscale
 * / indexed source (e.g. a 16-bit greyscale TIFF) always yields a
 * browser-safe 8-bit RGB raster — the same normalisation
 * {@code RasterImageThumbnailProvider} already performs implicitly when it
 * draws the scaled thumbnail into a {@code TYPE_INT_RGB} canvas. This is a
 * linear color-space remap (Java2D's default), not a domain-aware
 * calibrated-value window/level stretch — that stays out of scope here (see
 * the dedicated {@code shepard-plugin-fileformat-thermography} heatmap
 * renderer for calibrated OTvis frames).
 */
public final class SafeImageIO {

  private SafeImageIO() {}

  /**
   * Default decode cap: 25 megapixels. At {@code TYPE_INT_RGB} (4 bytes/px)
   * that is a ~100 MB worst-case raster per decode — headroom-safe against
   * the 4-worker {@code ThumbnailGenerationQueue} pool while comfortably
   * covering realistic manufacturing/inspection camera frames (a 24 MP
   * full-frame DSLR frame included).
   */
  public static final long DEFAULT_MAX_PIXELS = 25_000_000L;

  /** Thrown when the source image's pixel count exceeds the caller's cap. */
  public static class ImageTooLargeException extends IOException {
    public ImageTooLargeException(String message) {
      super(message);
    }
  }

  /**
   * Decode {@code in} to a {@link BufferedImage}, refusing to allocate a
   * raster larger than {@code maxPixels} (width * height).
   *
   * @param in raw image bytes (any ImageIO-registered format, including
   *   TIFF once the TwelveMonkeys {@code imageio-tiff} plugin is on the
   *   classpath). Caller is responsible for closing the stream.
   * @param maxPixels inclusive cap on {@code width * height}.
   * @return the decoded first frame/directory (image index 0).
   * @throws ImageTooLargeException when the source declares dimensions
   *   whose product exceeds {@code maxPixels}.
   * @throws IOException when no reader claims the stream or decoding fails.
   */
  public static BufferedImage readCapped(InputStream in, long maxPixels) throws IOException {
    try (ImageInputStream iis = ImageIO.createImageInputStream(in)) {
      if (iis == null) {
        throw new IOException("ImageIO could not open an input stream for the given source");
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
      if (!readers.hasNext()) {
        throw new IOException("No ImageIO reader registered for this format");
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(iis, true, true);
        long pixels = (long) reader.getWidth(0) * (long) reader.getHeight(0);
        if (pixels > maxPixels) {
          throw new ImageTooLargeException(
            "Source image is " + reader.getWidth(0) + "x" + reader.getHeight(0) +
            " (" + pixels + " px), exceeds the " + maxPixels + " px decode cap"
          );
        }
        BufferedImage img = reader.read(0);
        if (img == null) {
          throw new IOException("ImageIO reader returned a null image");
        }
        return img;
      } finally {
        reader.dispose();
      }
    }
  }

  /**
   * {@link #readCapped} followed by an unconditional normalisation to
   * 8-bit {@code TYPE_INT_RGB} at the source's native resolution (no
   * scaling). Used by the TIFF {@code ?rendition=png} full-size preview
   * transcode so the encoded PNG is always browser-renderable regardless
   * of the source TIFF's bit depth / color model.
   *
   * @param in raw image bytes; caller is responsible for closing the stream.
   * @param maxPixels inclusive cap on {@code width * height}.
   * @return an 8-bit RGB copy of the decoded image, same dimensions.
   * @throws IOException per {@link #readCapped}.
   */
  public static BufferedImage readCappedAsRgb8(InputStream in, long maxPixels) throws IOException {
    BufferedImage src = readCapped(in, maxPixels);
    if (src.getType() == BufferedImage.TYPE_INT_RGB) {
      return src;
    }
    BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    try {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, src.getWidth(), src.getHeight());
      g.drawImage(src, 0, 0, null);
    } finally {
      g.dispose();
    }
    return out;
  }
}
