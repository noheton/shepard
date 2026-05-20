package de.dlr.shepard.data.file.thumbnail;

import jakarta.enterprise.context.ApplicationScoped;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * TH1a — thumbnail provider for plain-text files.
 *
 * <p>Renders the first {@value #MAX_LINES} lines of the file as a miniature
 * code-preview image on a white background using a monospace font. Font size
 * scales with the requested {@code sizePx} so the result is readable at 200 px.
 */
@ApplicationScoped
public class TextThumbnailProvider implements ThumbnailProvider {

  static final int MAX_LINES = 20;
  static final int PADDING = 6;

  private static final Set<String> MIME_TYPES = Set.of(
    "text/plain", "text/markdown", "text/x-yaml", "application/yaml",
    "application/json", "text/csv"
  );

  private static final Set<String> EXTENSIONS = Set.of(
    "txt", "md", "yml", "yaml", "json", "toml", "csv", "log", "ini", "conf", "sh", "py", "java", "ts", "js"
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
    String raw = new String(fileBytes.readNBytes(16_384), StandardCharsets.UTF_8);
    String[] allLines = raw.split("\r?\n", -1);

    List<String> lines = new ArrayList<>(MAX_LINES);
    for (int i = 0; i < Math.min(MAX_LINES, allLines.length); i++) {
      String line = allLines[i];
      if (line.length() > 60) line = line.substring(0, 57) + "…";
      lines.add(line);
    }

    int fontSize = Math.max(6, sizePx / 20);
    Font font = new Font(Font.MONOSPACED, Font.PLAIN, fontSize);

    // measure to determine canvas size
    BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    Graphics2D pg = probe.createGraphics();
    pg.setFont(font);
    FontMetrics fm = pg.getFontMetrics();
    int lineH = fm.getHeight();
    int maxW = 0;
    for (String line : lines) {
      maxW = Math.max(maxW, fm.stringWidth(line));
    }
    pg.dispose();

    int imgW = maxW + 2 * PADDING;
    int imgH = lines.size() * lineH + 2 * PADDING;
    // cap at sizePx × sizePx
    imgW = Math.min(imgW, sizePx);
    imgH = Math.min(imgH, sizePx);
    imgW = Math.max(1, imgW);
    imgH = Math.max(1, imgH);

    BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, imgW, imgH);
    g.setFont(font);
    g.setColor(new Color(0x33, 0x33, 0x33));
    for (int i = 0; i < lines.size(); i++) {
      g.drawString(lines.get(i), PADDING, PADDING + (i + 1) * lineH - fm.getDescent());
    }
    g.dispose();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, "PNG", baos);
    return baos.toByteArray();
  }
}
