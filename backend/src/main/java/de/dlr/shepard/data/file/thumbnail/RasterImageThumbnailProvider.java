package de.dlr.shepard.data.file.thumbnail;

import de.dlr.shepard.common.util.SafeImageIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * TH1a — thumbnail provider for raster image formats supported by Java ImageIO.
 *
 * <p>Scales using area-averaging for speed (BILINEAR would be marginally better
 * quality but adds a dependency on java.awt.RenderingHints; both are acceptable
 * for 64/200/400 px thumbnails).
 *
 * <p>TIFF-PREVIEW-SUPPORT (2026-07-10): {@code image/tiff} + {@code image/tif}
 * joined the supported set once the {@code com.twelvemonkeys.imageio:imageio-tiff}
 * dependency (already on the backend classpath for MFFD-NDT-QUALITY-1's
 * thermography frame decoding) registers a TIFF {@code ImageReader} with
 * {@link ImageIO} — no code change was needed beyond widening the claimed
 * mime/extension sets, since {@link ImageIO#read} transparently picks up the
 * new reader. The decode now goes through {@link SafeImageIO#readCapped} so
 * a single malformed or absurdly large source (multi-directory TIFFs can be
 * huge) cannot exhaust the shared {@link ThumbnailGenerationQueue} worker
 * pool — per the CLAUDE.md fail-soft posture, a cap violation surfaces as an
 * {@link IOException} that {@link ThumbnailService} already catches and
 * turns into "no thumbnail" (404), never a 500.
 */
@ApplicationScoped
public class RasterImageThumbnailProvider implements ThumbnailProvider {

  private static final Set<String> MIME_TYPES = Set.of(
    "image/png", "image/jpeg", "image/gif", "image/bmp", "image/webp",
    "image/tiff", "image/tif"
  );

  private static final Set<String> EXTENSIONS = Set.of(
    "png", "jpg", "jpeg", "gif", "bmp", "webp", "tif", "tiff"
  );

  @Override
  public Set<String> supportedMimeTypes() {
    return MIME_TYPES;
  }

  @Override
  public Set<String> supportedExtensions() {
    return EXTENSIONS;
  }

  @Override
  public byte[] generate(InputStream fileBytes, String filename, int sizePx) throws IOException {
    BufferedImage src;
    try {
      src = SafeImageIO.readCapped(fileBytes, SafeImageIO.DEFAULT_MAX_PIXELS);
    } catch (IOException e) {
      throw new IOException("ImageIO could not decode file: " + filename + " — " + e.getMessage(), e);
    }

    int srcW = src.getWidth();
    int srcH = src.getHeight();
    int tgtW, tgtH;
    if (srcW >= srcH) {
      tgtW = Math.min(sizePx, srcW);
      tgtH = (int) Math.round((double) srcH * tgtW / srcW);
    } else {
      tgtH = Math.min(sizePx, srcH);
      tgtW = (int) Math.round((double) srcW * tgtH / srcH);
    }
    tgtW = Math.max(1, tgtW);
    tgtH = Math.max(1, tgtH);

    java.awt.Image scaled = src.getScaledInstance(tgtW, tgtH, java.awt.Image.SCALE_AREA_AVERAGING);
    BufferedImage out = new BufferedImage(tgtW, tgtH, BufferedImage.TYPE_INT_RGB);
    java.awt.Graphics2D g = out.createGraphics();
    g.setColor(java.awt.Color.WHITE);
    g.fillRect(0, 0, tgtW, tgtH);
    g.drawImage(scaled, 0, 0, null);
    g.dispose();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    if (!ImageIO.write(out, "PNG", baos)) {
      Log.warnf("RasterImageThumbnailProvider: ImageIO.write returned false for %s", filename);
    }
    return baos.toByteArray();
  }
}
