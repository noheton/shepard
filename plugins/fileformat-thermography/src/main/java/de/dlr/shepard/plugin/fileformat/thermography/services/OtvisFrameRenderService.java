package de.dlr.shepard.plugin.fileformat.thermography.services;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.plugin.fileformat.thermography.ExtractedFrames;
import de.dlr.shepard.plugin.fileformat.thermography.LockInResultFrame;
import de.dlr.shepard.plugin.fileformat.thermography.OTvisFrameExtractor;
import de.dlr.shepard.plugin.fileformat.thermography.RawCalibratedFrame;
import de.dlr.shepard.plugin.fileformat.thermography.io.OtvisFramesIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * OTVIS-VIEWER — decode an Edevis OTvis archive (resolved from a singleton
 * {@link de.dlr.shepard.context.references.file.entities.FileReference}) into
 * amplitude/phase/temperature heatmap PNGs for the frontend viewer.
 *
 * <p><b>Serving-shape decision.</b> We render the colour-mapped heatmap
 * server-side and return a PNG rather than shipping raw {@code float[]}
 * arrays to the browser. Rationale:
 * <ul>
 *   <li>A 640×512 lock-in frame is ~328k pixels — as float32 that is ~1.3 MB
 *       per channel over the wire and forces the browser to re-implement the
 *       colour map. As an indexed/RGB PNG it is tens of KB and the browser
 *       just blits it.</li>
 *   <li>The colour map (inferno for magnitude, a cyclic map for phase) lives
 *       once, server-side, next to the physics — no client/server drift.</li>
 *   <li>This is the cheapest viable path that respects "UI never asks for
 *       paths/URLs": the viewer hands an {@code appId}, the backend resolves
 *       bytes via {@link SingletonFileReferenceService#getPayload(String)}
 *       and decodes via the pure-Java {@link OTvisFrameExtractor}.</li>
 * </ul>
 *
 * <p>Full OME-Zarr (chunked, multiscale, pyramidal) is the right shape for a
 * 6,000-frame transient stack a researcher pans/zooms in napari — but it is
 * overkill for a v1 "see the inspection" viewer and is deferred as
 * {@code OTVIS-TIER2-OMEZARR-ZARR} (see findings + aidocs/16). The OTvis
 * fixtures here are single-frame-per-file, so the cheap per-frame PNG path
 * fully covers the MFFD NDT use case today.
 *
 * <p>Decoding is on the request path. OTvis archives are bounded
 * (single-digit MB to ~100 MB) and the MFFD fixture is one frame per archive,
 * so a re-decode per request is acceptable for v1. If a multi-frame archive
 * makes this expensive, the follow-up is a decoded-frame cache keyed by
 * fileReference appId — noted in findings, not built here.
 */
@RequestScoped
public class OtvisFrameRenderService {

  static final String CH_AMPLITUDE = "amplitude";
  static final String CH_PHASE = "phase";
  static final String CH_TEMPERATURE = "temperature";

  @Inject
  SingletonFileReferenceService singletonFileReferenceService;

  /** Test seam — overridable extractor (pure POJO, no CDI). */
  OTvisFrameExtractor extractor = new OTvisFrameExtractor();

  /**
   * Decode the archive and return the frame index. Throws
   * {@link InvalidBodyException} when the archive is not a usable OTvis tar.
   */
  public OtvisFramesIO listFrames(String fileReferenceAppId) {
    ExtractedFrames frames = decode(fileReferenceAppId);
    return buildIndex(fileReferenceAppId, frames);
  }

  /**
   * Render one frame/channel to a PNG byte array. Throws
   * {@link InvalidBodyException} for a bad archive or out-of-range frame.
   */
  public byte[] renderPng(String fileReferenceAppId, int frameIndex, String channel) {
    ExtractedFrames frames = decode(fileReferenceAppId);
    return renderFromExtracted(frames, frameIndex, channel);
  }

  // ── V2CONV-A7-THERMO — stream-rooted decode (render-SPI E3 seam) ──────────

  /**
   * V2CONV-A7-THERMO — decode an OTvis archive supplied as a raw stream and
   * return the frame index. Used by the {@code OtvisFrameRenderer}
   * {@link de.dlr.shepard.spi.view.ViewRecipeRenderer}, which obtains the bytes
   * from the render dispatcher's {@link de.dlr.shepard.spi.view.FocusPayloadResolver}
   * (E3) rather than resolving the FileReference itself — the renderer is a
   * ServiceLoader POJO with no CDI scope.
   *
   * @param fileReferenceAppId echoed into the index for the viewer (display only)
   * @param in                 the .OTvis archive byte stream; caller owns it
   * @return the decoded frame index
   * @throws InvalidBodyException when the stream is not a usable OTvis tar
   */
  public OtvisFramesIO listFramesFromStream(String fileReferenceAppId, InputStream in) {
    ExtractedFrames frames = decodeStream(in);
    return buildIndex(fileReferenceAppId, frames);
  }

  /**
   * V2CONV-A7-THERMO — render one frame/channel to PNG from a raw OTvis stream.
   * Stream sibling of {@link #renderPng(String, int, String)}.
   *
   * @param in         the .OTvis archive byte stream; caller owns it
   * @param frameIndex zero-based frame index
   * @param channel    amplitude|phase (lock-in) or temperature (raw); null → frame default
   * @return the colour-mapped heatmap PNG bytes
   * @throws InvalidBodyException for a bad archive, out-of-range frame, or bad channel
   */
  public byte[] renderPngFromStream(InputStream in, int frameIndex, String channel) {
    ExtractedFrames frames = decodeStream(in);
    return renderFromExtracted(frames, frameIndex, channel);
  }

  ExtractedFrames decodeStream(InputStream in) {
    try {
      return extractor.extract(in);
    } catch (IOException ex) {
      Log.warnf("OTVIS-VIEWER: supplied stream is not a usable OTvis archive — %s", ex.getMessage());
      throw new InvalidBodyException("stream is not a decodable OTvis archive: " + ex.getMessage());
    }
  }

  // ── decode + resolve ─────────────────────────────────────────────────────

  ExtractedFrames decode(String fileReferenceAppId) {
    NamedInputStream nis = singletonFileReferenceService.getPayload(fileReferenceAppId);
    try (InputStream in = nis.getInputStream()) {
      return extractor.extract(in);
    } catch (IOException ex) {
      Log.warnf("OTVIS-VIEWER: appId=%s is not a usable OTvis archive — %s",
        fileReferenceAppId, ex.getMessage());
      throw new InvalidBodyException(
        "FileReference " + fileReferenceAppId + " is not a decodable OTvis archive: "
          + ex.getMessage());
    }
  }

  // ── index ────────────────────────────────────────────────────────────────

  OtvisFramesIO buildIndex(String fileReferenceAppId, ExtractedFrames frames) {
    List<OtvisFramesIO.FrameInfo> infos = new ArrayList<>();
    int idx = 0;
    for (int i = 0; i < frames.lockInResult.size(); i++) {
      infos.add(new OtvisFramesIO.FrameInfo(
        idx++, "lockin", List.of(CH_AMPLITUDE, CH_PHASE), CH_PHASE));
    }
    for (int i = 0; i < frames.rawCalibrated.size(); i++) {
      infos.add(new OtvisFramesIO.FrameInfo(
        idx++, "raw", List.of(CH_TEMPERATURE), CH_TEMPERATURE));
    }
    OtvisFramesIO io = new OtvisFramesIO();
    io.setFileReferenceAppId(fileReferenceAppId);
    io.setWidth(frames.width);
    io.setHeight(frames.height);
    io.setFrameCount(infos.size());
    io.setFrames(infos);
    io.setPartialReason(frames.partialReason);
    return io;
  }

  // ── render ─────────────────────────────────────────────────────────────────

  byte[] renderFromExtracted(ExtractedFrames frames, int frameIndex, String channel) {
    int lockInCount = frames.lockInResult.size();
    int total = lockInCount + frames.rawCalibrated.size();
    if (frameIndex < 0 || frameIndex >= total) {
      throw new InvalidBodyException(
        "frame index " + frameIndex + " out of range [0," + total + ")");
    }

    final float[] data;
    final int w;
    final int h;
    final boolean cyclic;

    if (frameIndex < lockInCount) {
      LockInResultFrame f = frames.lockInResult.get(frameIndex);
      w = f.header.width();
      h = f.header.height();
      if (CH_PHASE.equalsIgnoreCase(channel)) {
        data = f.phase;
        cyclic = true; // phase ∈ (-π, π] — use a cyclic-friendly ramp
      } else if (channel == null || CH_AMPLITUDE.equalsIgnoreCase(channel)) {
        data = f.amplitude;
        cyclic = false;
      } else {
        throw new InvalidBodyException(
          "channel '" + channel + "' not valid for lock-in frame; use amplitude|phase");
      }
    } else {
      RawCalibratedFrame f = frames.rawCalibrated.get(frameIndex - lockInCount);
      w = f.header.width();
      h = f.header.height();
      if (channel == null || CH_TEMPERATURE.equalsIgnoreCase(channel)) {
        data = f.temperatureCelsius;
        cyclic = false;
      } else {
        throw new InvalidBodyException(
          "channel '" + channel + "' not valid for raw frame; use temperature");
      }
    }

    return toPng(data, w, h, cyclic);
  }

  /**
   * Colour-map a float array to an RGB PNG. Min/max are computed over finite
   * samples; an all-equal frame maps to mid-grey rather than dividing by zero.
   */
  static byte[] toPng(float[] data, int w, int h, boolean cyclic) {
    float min = Float.POSITIVE_INFINITY;
    float max = Float.NEGATIVE_INFINITY;
    for (float v : data) {
      if (Float.isNaN(v) || Float.isInfinite(v)) continue;
      if (v < min) min = v;
      if (v > max) max = v;
    }
    if (!Float.isFinite(min) || !Float.isFinite(max)) {
      min = 0f;
      max = 1f;
    }
    float range = max - min;

    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        float v = data[y * w + x];
        float t = (range > 0f && Float.isFinite(v)) ? (v - min) / range : 0.5f;
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        int rgb = cyclic ? cyclicRgb(t) : infernoRgb(t);
        img.setRGB(x, y, rgb);
      }
    }

    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ImageIO.write(img, "png", baos);
      return baos.toByteArray();
    } catch (IOException ex) {
      // PNG encoding to memory does not do real I/O; failure here is fatal.
      throw new InvalidBodyException("failed to encode heatmap PNG: " + ex.getMessage());
    }
  }

  // ── colour maps (server-side; mirror frontend utils/colormap.ts inferno) ────

  /** Inferno perceptual ramp — magnitude/temperature channels. */
  static int infernoRgb(float t) {
    float[][] stops = {
      {0.001f, 0.000f, 0.014f},
      {0.341f, 0.064f, 0.429f},
      {0.725f, 0.280f, 0.198f},
      {0.962f, 0.619f, 0.042f},
      {0.988f, 1.000f, 0.644f},
    };
    return interp(stops, t);
  }

  /**
   * Cyclic-friendly ramp for phase. Phase wraps at ±π, so a perceptual map
   * whose endpoints are visually similar reads better than inferno (whose
   * dark-start / bright-end implies a discontinuity that phase does not have).
   * We use a symmetric blue→white→red→white→blue twilight-style ramp.
   */
  static int cyclicRgb(float t) {
    float[][] stops = {
      {0.18f, 0.20f, 0.45f},
      {0.45f, 0.55f, 0.85f},
      {0.95f, 0.95f, 0.95f},
      {0.85f, 0.40f, 0.40f},
      {0.18f, 0.20f, 0.45f},
    };
    return interp(stops, t);
  }

  static int interp(float[][] stops, float t) {
    int n = stops.length;
    float clampedT = t < 0f ? 0f : (t > 1f ? 1f : t);
    float scaled = clampedT * (n - 1);
    int i = (int) Math.floor(scaled);
    if (i >= n - 1) i = n - 2;
    if (i < 0) i = 0;
    float a = scaled - i;
    float[] c0 = stops[i];
    float[] c1 = stops[i + 1];
    int r = clamp255((c0[0] + a * (c1[0] - c0[0])) * 255f);
    int g = clamp255((c0[1] + a * (c1[1] - c0[1])) * 255f);
    int b = clamp255((c0[2] + a * (c1[2] - c0[2])) * 255f);
    return (r << 16) | (g << 8) | b;
  }

  static int clamp255(float v) {
    int i = Math.round(v);
    if (i < 0) return 0;
    if (i > 255) return 255;
    return i;
  }
}
