package de.dlr.shepard.data.file.thumbnail;

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
 */
@ApplicationScoped
public class RasterImageThumbnailProvider implements ThumbnailProvider {

  private static final Set<String> MIME_TYPES = Set.of(
    "image/png", "image/jpeg", "image/gif", "image/bmp", "image/webp"
  );

  private static final Set<String> EXTENSIONS = Set.of(
    "png", "jpg", "jpeg", "gif", "bmp", "webp"
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
    BufferedImage src = ImageIO.read(fileBytes);
    if (src == null) {
      throw new IOException("ImageIO could not decode file: " + filename);
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
