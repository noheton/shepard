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
}
