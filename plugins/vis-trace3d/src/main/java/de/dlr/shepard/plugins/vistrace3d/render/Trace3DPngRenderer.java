package de.dlr.shepard.plugins.vistrace3d.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.spi.view.RenderRequest;
import de.dlr.shepard.spi.view.RenderResponse;
import de.dlr.shepard.spi.view.RenderResponse.ChannelBindingProjection;
import de.dlr.shepard.spi.view.RenderedMedia;
import de.dlr.shepard.spi.view.ViewRecipeRenderer;
import io.quarkus.logging.Log;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * RESEED-FIND-RENDER-PNG — the first {@link ViewRecipeRenderer} that emits a real
 * {@code image/png} for the {@code Trace3DViewShape} VIEW_RECIPE shape.
 *
 * <p>Before this renderer existed, {@code POST /v2/shapes/render} with
 * {@code Accept: image/png} always fell back to the JSON view-model: no installed
 * renderer declared {@code image/png} in {@link #producibleMedia()}, so the
 * dispatcher's {@code negotiateMedia()} path had nothing to call. This renderer
 * closes that gap — it claims the Trace3D shape IRI
 * ({@code http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape}, the IRI
 * the {@code feat-trace3d} + {@code feat-render-media} seeds target) and produces
 * a pure-JVM raster of the view recipe.
 *
 * <h2>What the PNG shows</h2>
 *
 * <p>The render endpoint is stateless and (in the current TPL2b/DECLARED beta
 * posture) does <b>not</b> resolve live channel samples — the dispatcher passes
 * the template body, not the focus DataObject's timeseries bytes. A server-side
 * raster therefore can't plot real sample values yet. Instead this renderer
 * produces a faithful <b>view-recipe card</b>: the recipe title, the declared
 * colour map, and a 2-D projection of the declared x / y / z (+ colour) channel
 * bindings drawn as a labelled axis diagram with a synthetic illustrative path.
 * It is a deterministic, headless-browser-free PNG that proves the
 * content-negotiation path end-to-end. When live channel resolution lands
 * (VIS-S1 / TPL2c), the synthetic path is replaced by the resolved frame
 * polyline with no change to the negotiation contract.
 *
 * <p>Rasterisation is {@link BufferedImage} + AWT {@link Graphics2D} +
 * {@link ImageIO}, all pure-JVM — no headless browser, no native dependency.
 *
 * <h2>Registration</h2>
 *
 * <p>Registered through
 * {@code META-INF/services/de.dlr.shepard.spi.view.ViewRecipeRenderer} — the same
 * ServiceLoader mechanism {@link ViewRecipeRendererRegistry} walks at startup,
 * and the same shape the sibling {@code SceneGraphPlayTransformExecutor} uses for
 * the transform SPI.
 *
 * @see ViewRecipeRenderer
 * @see de.dlr.shepard.spi.view.ViewRecipeRendererRegistry
 */
public final class Trace3DPngRenderer implements ViewRecipeRenderer {

  /**
   * The Trace3D VIEW_RECIPE shape IRI this renderer claims — the same IRI the
   * {@code feat-trace3d} + {@code feat-render-media} seeds target and that
   * {@code plugins/vis-trace3d/src/main/resources/shapes/trace-3d-view.shacl.ttl}
   * defines.
   */
  public static final String TRACE3D_VIEW_SHAPE_IRI =
    "http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape";

  /** PNG media type this renderer emits. */
  public static final String MEDIA_PNG = "image/png";

  private static final int WIDTH = 800;
  private static final int HEIGHT = 600;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public Set<String> supportedShapeIris() {
    return Set.of(TRACE3D_VIEW_SHAPE_IRI);
  }

  @Override
  public String name() {
    return "Trace3DPngRenderer";
  }

  /**
   * The JSON view-model path. Mirrors the in-tree DECLARED projection: one
   * binding entry per declared channel binding, status DECLARED (live channel
   * resolution is deferred to TPL2c). The dispatcher calls this for
   * {@code Accept: application/json}; {@link #renderMedia} handles
   * {@code Accept: image/png}.
   */
  @Override
  public RenderResponse render(RenderRequest req) {
    JsonNode body = parseBody(req.templateBodyJson());
    String renderer = textOrNull(body, "renderer");
    List<ChannelBindingProjection> bindings = new ArrayList<>();
    for (Binding b : parseBindings(body)) {
      bindings.add(new ChannelBindingProjection(b.role, b.selector, b.unit, b.required, "DECLARED", null));
    }
    return new RenderResponse(req.templateAppId(), req.focusShepardId(), renderer, bindings);
  }

  @Override
  public Set<String> producibleMedia() {
    return Set.of(MEDIA_PNG);
  }

  @Override
  public Optional<RenderedMedia> renderMedia(RenderRequest req, String acceptMediaType) {
    if (!MEDIA_PNG.equals(acceptMediaType)) {
      return Optional.empty();
    }
    try {
      byte[] png = rasterise(parseBody(req.templateBodyJson()));
      if (png.length == 0) {
        return Optional.empty();
      }
      return Optional.of(new RenderedMedia(MEDIA_PNG, png));
    } catch (IOException | RuntimeException ex) {
      // Fail-soft: an encoder hiccup falls back to the JSON view-model rather
      // than failing the whole render call.
      Log.warnf(ex, "RESEED-FIND-RENDER-PNG: PNG rasterisation failed for shape <%s> — falling back to JSON", TRACE3D_VIEW_SHAPE_IRI);
      return Optional.empty();
    }
  }

  // ─── rasterisation ───────────────────────────────────────────────────────────

  /**
   * Render the view-recipe card to PNG bytes. Pure AWT — a titled axis frame,
   * the declared channel-binding legend, and an illustrative projected path.
   */
  byte[] rasterise(JsonNode body) throws IOException {
    BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      // Background
      g.setColor(new Color(0x12, 0x16, 0x1c));
      g.fillRect(0, 0, WIDTH, HEIGHT);

      // Title
      String title = firstNonBlank(textOrNull(body, "title"), "Trace3D view recipe");
      g.setColor(Color.WHITE);
      g.setFont(new Font("SansSerif", Font.BOLD, 22));
      g.drawString(truncate(title, 60), 32, 44);

      String colorMap = firstNonBlank(textOrNull(body, "trace3d:colorMap"), "viridis");
      g.setFont(new Font("SansSerif", Font.PLAIN, 13));
      g.setColor(new Color(0x9a, 0xa5, 0xb1));
      g.drawString("colourMap=" + colorMap + "   shape=Trace3DViewShape", 32, 66);

      // Plot frame
      int plotX = 80;
      int plotY = 100;
      int plotW = WIDTH - plotX - 240;
      int plotH = HEIGHT - plotY - 80;
      g.setColor(new Color(0x2a, 0x31, 0x3c));
      g.setStroke(new BasicStroke(1.5f));
      g.drawRect(plotX, plotY, plotW, plotH);

      // Axis labels (x / y) — z is implied by the colour ramp on a 2-D raster.
      g.setColor(new Color(0x6b, 0x76, 0x82));
      g.setFont(new Font("SansSerif", Font.PLAIN, 12));
      g.drawString("x", plotX + plotW / 2, plotY + plotH + 28);
      g.drawString("y", plotX - 24, plotY + plotH / 2);

      List<Binding> bindings = parseBindings(body);

      // Illustrative projected path — deterministic Lissajous-style curve so the
      // PNG always has a recognisable trace even before live channel resolution.
      drawIllustrativePath(g, plotX, plotY, plotW, plotH, colorMap);

      // Legend — the declared channel bindings.
      drawLegend(g, plotX + plotW + 24, plotY, bindings);

      // Footer note.
      g.setColor(new Color(0x55, 0x5f, 0x6a));
      g.setFont(new Font("SansSerif", Font.ITALIC, 11));
      g.drawString(
        "Server-side raster (RESEED-FIND-RENDER-PNG). Path is illustrative until live channel resolution (TPL2c).",
        32,
        HEIGHT - 24
      );
    } finally {
      g.dispose();
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    boolean wrote = ImageIO.write(img, "png", baos);
    if (!wrote) {
      throw new IOException("no PNG ImageWriter available");
    }
    return baos.toByteArray();
  }

  private void drawIllustrativePath(Graphics2D g, int px, int py, int pw, int ph, String colorMap) {
    int n = 200;
    Color[] ramp = colourRamp(colorMap);
    g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    double prevX = 0;
    double prevY = 0;
    for (int i = 0; i <= n; i++) {
      double t = (double) i / n;
      // Deterministic Lissajous-style projection inside the plot box.
      double x = px + (0.5 + 0.42 * Math.sin(2 * Math.PI * 3 * t)) * pw;
      double y = py + (0.5 + 0.42 * Math.sin(2 * Math.PI * 2 * t + Math.PI / 4)) * ph;
      if (i > 0) {
        Color c = ramp[(int) Math.round(t * (ramp.length - 1))];
        g.setColor(c);
        g.drawLine((int) prevX, (int) prevY, (int) x, (int) y);
      }
      prevX = x;
      prevY = y;
    }
  }

  private void drawLegend(Graphics2D g, int x, int y, List<Binding> bindings) {
    g.setColor(Color.WHITE);
    g.setFont(new Font("SansSerif", Font.BOLD, 14));
    g.drawString("Channel bindings", x, y + 16);
    g.setFont(new Font("SansSerif", Font.PLAIN, 12));
    int row = y + 42;
    if (bindings.isEmpty()) {
      g.setColor(new Color(0x6b, 0x76, 0x82));
      g.drawString("(none declared)", x, row);
      return;
    }
    Color[] roleSwatch = {
      new Color(0xe8, 0x6a, 0x6a),
      new Color(0x6a, 0xc8, 0x8a),
      new Color(0x6a, 0x9a, 0xe8),
      new Color(0xe8, 0xc8, 0x6a),
    };
    int idx = 0;
    for (Binding b : bindings) {
      g.setColor(roleSwatch[idx % roleSwatch.length]);
      g.fillRect(x, row - 10, 12, 12);
      g.setColor(new Color(0xcb, 0xd2, 0xd9));
      String label = (b.role == null ? "?" : b.role) + (b.required ? " *" : "");
      g.drawString(truncate(label, 28), x + 20, row);
      row += 24;
      idx++;
    }
  }

  /** A tiny built-in colour ramp keyed loosely by name. Headless, no LUT file. */
  private Color[] colourRamp(String name) {
    String n = name == null ? "viridis" : name.toLowerCase();
    Color a;
    Color b;
    Color c;
    switch (n) {
      case "inferno":
      case "magma":
      case "heat":
        a = new Color(0x00, 0x00, 0x04);
        b = new Color(0xbb, 0x3a, 0x35);
        c = new Color(0xfc, 0xff, 0xa4);
        break;
      case "plasma":
        a = new Color(0x0d, 0x08, 0x87);
        b = new Color(0xcc, 0x49, 0x78);
        c = new Color(0xf0, 0xf9, 0x21);
        break;
      case "cool":
      case "rdbu":
        a = new Color(0x05, 0x71, 0xb0);
        b = new Color(0xf7, 0xf7, 0xf7);
        c = new Color(0xca, 0x00, 0x20);
        break;
      case "viridis":
      default:
        a = new Color(0x44, 0x01, 0x54);
        b = new Color(0x21, 0x90, 0x8c);
        c = new Color(0xfd, 0xe7, 0x25);
        break;
    }
    int steps = 64;
    Color[] ramp = new Color[steps];
    for (int i = 0; i < steps; i++) {
      double t = (double) i / (steps - 1);
      Color lo = t < 0.5 ? a : b;
      Color hi = t < 0.5 ? b : c;
      double tt = t < 0.5 ? t * 2 : (t - 0.5) * 2;
      ramp[i] = new Color(
        lerp(lo.getRed(), hi.getRed(), tt),
        lerp(lo.getGreen(), hi.getGreen(), tt),
        lerp(lo.getBlue(), hi.getBlue(), tt)
      );
    }
    return ramp;
  }

  private static int lerp(int lo, int hi, double t) {
    return (int) Math.round(lo + (hi - lo) * t);
  }

  // ─── body parsing ─────────────────────────────────────────────────────────────

  private record Binding(String role, String selector, String unit, boolean required) {}

  private JsonNode parseBody(String bodyJson) {
    if (bodyJson == null || bodyJson.isBlank()) {
      return MAPPER.createObjectNode();
    }
    try {
      return MAPPER.readTree(bodyJson);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      return MAPPER.createObjectNode();
    }
  }

  private List<Binding> parseBindings(JsonNode body) {
    List<Binding> out = new ArrayList<>();
    JsonNode arr = body.path("channelBindings");
    if (!arr.isArray()) {
      return out;
    }
    for (JsonNode b : arr) {
      String role = textOrNull(b, "role");
      String selector = textOrNull(b, "channelSelector");
      String unit = textOrNull(b, "unit");
      boolean required = b.path("required").asBoolean(true);
      out.add(new Binding(role, selector, unit, required));
    }
    return out;
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode v = node.path(field);
    if (!v.isTextual()) {
      return null;
    }
    String s = v.asText();
    return (s == null || s.isBlank()) ? null : s;
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) {
      return a;
    }
    return b;
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return "";
    }
    return s.length() <= max ? s : s.substring(0, max - 1) + "…";
  }
}
