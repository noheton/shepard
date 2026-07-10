package de.dlr.shepard.data.file.thumbnail;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class RasterImageThumbnailProviderTest {

  private final RasterImageThumbnailProvider provider = new RasterImageThumbnailProvider();

  @Test
  void generateScalesPngToRequestedSize() throws Exception {
    byte[] png = makePng(800, 600);
    byte[] thumb = provider.generate(new ByteArrayInputStream(png), "test.png", 200);

    assertNotNull(thumb);
    assertTrue(thumb.length > 0);

    BufferedImage result = ImageIO.read(new ByteArrayInputStream(thumb));
    assertNotNull(result);
    // longest side should be at most 200
    assertTrue(Math.max(result.getWidth(), result.getHeight()) <= 200);
  }

  @Test
  void generateSquarePng() throws Exception {
    byte[] png = makePng(300, 300);
    byte[] thumb = provider.generate(new ByteArrayInputStream(png), "square.png", 64);

    BufferedImage result = ImageIO.read(new ByteArrayInputStream(thumb));
    assertNotNull(result);
    assertTrue(result.getWidth() <= 64);
    assertTrue(result.getHeight() <= 64);
  }

  @Test
  void generatePortraitPng() throws Exception {
    byte[] png = makePng(100, 400);
    byte[] thumb = provider.generate(new ByteArrayInputStream(png), "portrait.png", 200);

    BufferedImage result = ImageIO.read(new ByteArrayInputStream(thumb));
    assertNotNull(result);
    assertTrue(result.getHeight() <= 200);
  }

  @Test
  void generateThrowsOnGarbage() {
    InputStream garbage = new ByteArrayInputStream(new byte[]{0x00, 0x01, 0x02});
    assertThrows(IOException.class, () -> provider.generate(garbage, "file.png", 200));
  }

  @Test
  void supportedMimeTypesContainsCommonImages() {
    assertTrue(provider.supportedMimeTypes().contains("image/png"));
    assertTrue(provider.supportedMimeTypes().contains("image/jpeg"));
    assertTrue(provider.supportedMimeTypes().contains("image/gif"));
  }

  @Test
  void supportedExtensionsContainsCommonImages() {
    assertTrue(provider.supportedExtensions().contains("png"));
    assertTrue(provider.supportedExtensions().contains("jpg"));
    assertTrue(provider.supportedExtensions().contains("jpeg"));
  }

  // ─── TIFF-PREVIEW-SUPPORT ──────────────────────────────────────────────────

  @Test
  void supportedMimeTypesContainsTiff() {
    assertTrue(provider.supportedMimeTypes().contains("image/tiff"));
    assertTrue(provider.supportedMimeTypes().contains("image/tif"));
  }

  @Test
  void supportedExtensionsContainsTiff() {
    assertTrue(provider.supportedExtensions().contains("tif"));
    assertTrue(provider.supportedExtensions().contains("tiff"));
  }

  @Test
  void generateScalesTiffToRequestedSize() throws Exception {
    byte[] tiff = makeTiff(2048, 873, Color.ORANGE); // MFFD TPS evaluation-file shape
    byte[] thumb = provider.generate(new ByteArrayInputStream(tiff), "tps_eval_0001.tiff", 200);

    assertNotNull(thumb);
    assertTrue(thumb.length > 0);

    BufferedImage result = ImageIO.read(new ByteArrayInputStream(thumb));
    assertNotNull(result);
    assertTrue(Math.max(result.getWidth(), result.getHeight()) <= 200);
    // thumbnail is always encoded as PNG regardless of source format
    assertTrue(looksLikePng(thumb));
  }

  @Test
  void generateGrayscale16BitTiff_normalizesTo8BitRgbPng() throws Exception {
    BufferedImage gray = new BufferedImage(300, 240, BufferedImage.TYPE_USHORT_GRAY);
    var g = gray.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 300, 240);
    g.dispose();
    ByteArrayOutputStream tiffBytes = new ByteArrayOutputStream();
    boolean wrote = ImageIO.write(gray, "TIFF", tiffBytes);
    assertTrue(wrote, "ImageIO must have a TIFF writer (TwelveMonkeys) on the test classpath");

    byte[] thumb = provider.generate(new ByteArrayInputStream(tiffBytes.toByteArray()), "frame.tif", 64);
    BufferedImage result = ImageIO.read(new ByteArrayInputStream(thumb));
    assertNotNull(result);
    assertTrue(result.getWidth() <= 64);
    assertTrue(result.getHeight() <= 64);
  }

  @Test
  void generateThrowsOnOversizedTiff() throws Exception {
    // 5100x5000 = 25_500_000 px, just over SafeImageIO.DEFAULT_MAX_PIXELS (25_000_000).
    byte[] hugeTiff = makeTiff(5100, 5000, Color.CYAN);
    assertThrows(
      IOException.class,
      () -> provider.generate(new ByteArrayInputStream(hugeTiff), "huge.tiff", 200)
    );
  }

  private static byte[] makePng(int width, int height) throws Exception {
    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var g = img.createGraphics();
    g.setColor(Color.BLUE);
    g.fillRect(0, 0, width, height);
    g.dispose();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, "PNG", baos);
    return baos.toByteArray();
  }

  private static byte[] makeTiff(int width, int height, Color color) throws Exception {
    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var g = img.createGraphics();
    g.setColor(color);
    g.fillRect(0, 0, width, height);
    g.dispose();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    boolean wrote = ImageIO.write(img, "TIFF", baos);
    if (!wrote) {
      throw new IllegalStateException("ImageIO has no TIFF writer on the test classpath");
    }
    return baos.toByteArray();
  }

  private static boolean looksLikePng(byte[] bytes) {
    // PNG magic: 89 50 4E 47 0D 0A 1A 0A
    return bytes.length > 8
      && (bytes[0] & 0xFF) == 0x89
      && bytes[1] == 'P'
      && bytes[2] == 'N'
      && bytes[3] == 'G';
  }
}
