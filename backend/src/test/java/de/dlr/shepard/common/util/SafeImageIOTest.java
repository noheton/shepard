package de.dlr.shepard.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * TIFF-PREVIEW-SUPPORT — tests for {@link SafeImageIO}, covering both PNG
 * (baseline ImageIO format) and TIFF (via the TwelveMonkeys {@code
 * imageio-tiff} plugin already on the backend classpath for
 * MFFD-NDT-QUALITY-1) round-trips, plus the OOM-guard pixel cap.
 */
class SafeImageIOTest {

  @Test
  void readCapped_decodesPng() throws Exception {
    byte[] png = writePng(makeImage(40, 30, Color.RED));
    BufferedImage decoded = SafeImageIO.readCapped(new ByteArrayInputStream(png), 10_000L);
    assertThat(decoded.getWidth()).isEqualTo(40);
    assertThat(decoded.getHeight()).isEqualTo(30);
  }

  @Test
  void readCapped_decodesTiff() throws Exception {
    byte[] tiff = writeTiff(makeImage(64, 48, Color.BLUE));
    BufferedImage decoded = SafeImageIO.readCapped(new ByteArrayInputStream(tiff), 10_000L);
    assertThat(decoded.getWidth()).isEqualTo(64);
    assertThat(decoded.getHeight()).isEqualTo(48);
  }

  @Test
  void readCapped_grayscaleTiff_decodes() throws Exception {
    BufferedImage gray = new BufferedImage(20, 20, BufferedImage.TYPE_BYTE_GRAY);
    var g = gray.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 20, 20);
    g.dispose();
    byte[] tiff = writeTiff(gray);
    BufferedImage decoded = SafeImageIO.readCapped(new ByteArrayInputStream(tiff), 10_000L);
    assertThat(decoded.getWidth()).isEqualTo(20);
    assertThat(decoded.getHeight()).isEqualTo(20);
  }

  @Test
  void readCapped_exceedsPixelCap_throwsImageTooLarge() throws Exception {
    byte[] png = writePng(makeImage(100, 100, Color.GREEN)); // 10_000 px
    assertThatThrownBy(() -> SafeImageIO.readCapped(new ByteArrayInputStream(png), 5_000L))
      .isInstanceOf(SafeImageIO.ImageTooLargeException.class)
      .hasMessageContaining("100x100");
  }

  @Test
  void readCapped_garbageBytes_throwsIOException() {
    byte[] garbage = { 0x00, 0x01, 0x02, 0x03 };
    assertThatThrownBy(() -> SafeImageIO.readCapped(new ByteArrayInputStream(garbage), 10_000L))
      .isInstanceOf(IOException.class);
  }

  @Test
  void readCappedAsRgb8_tiff_yieldsIntRgbType() throws Exception {
    BufferedImage gray = new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_GRAY);
    var g = gray.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 16, 16);
    g.dispose();
    byte[] tiff = writeTiff(gray);

    BufferedImage rgb = SafeImageIO.readCappedAsRgb8(new ByteArrayInputStream(tiff), 10_000L);
    assertThat(rgb.getType()).isEqualTo(BufferedImage.TYPE_INT_RGB);
    assertThat(rgb.getWidth()).isEqualTo(16);
    assertThat(rgb.getHeight()).isEqualTo(16);
  }

  @Test
  void readCappedAsRgb8_alreadyIntRgb_returnsSameShape() throws Exception {
    byte[] png = writePng(makeImage(12, 8, Color.MAGENTA));
    BufferedImage rgb = SafeImageIO.readCappedAsRgb8(new ByteArrayInputStream(png), 10_000L);
    assertThat(rgb.getType()).isEqualTo(BufferedImage.TYPE_INT_RGB);
    assertThat(rgb.getWidth()).isEqualTo(12);
    assertThat(rgb.getHeight()).isEqualTo(8);
  }

  // ─── fixtures ────────────────────────────────────────────────────────────

  private static BufferedImage makeImage(int w, int h, Color color) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    var g = img.createGraphics();
    g.setColor(color);
    g.fillRect(0, 0, w, h);
    g.dispose();
    return img;
  }

  private static byte[] writePng(BufferedImage img) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, "PNG", baos);
    return baos.toByteArray();
  }

  private static byte[] writeTiff(BufferedImage img) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    boolean wrote = ImageIO.write(img, "TIFF", baos);
    if (!wrote) {
      throw new IllegalStateException("ImageIO has no TIFF writer on the test classpath");
    }
    return baos.toByteArray();
  }
}
