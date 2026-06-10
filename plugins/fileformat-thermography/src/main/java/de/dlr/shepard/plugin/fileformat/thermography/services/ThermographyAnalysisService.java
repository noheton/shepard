package de.dlr.shepard.plugin.fileformat.thermography.services;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.context.version.entities.VersionableEntity;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileService;
import de.dlr.shepard.plugin.fileformat.thermography.io.AnalyzeResultIO;
import de.dlr.shepard.plugin.fileformat.thermography.io.PlateHeatmapIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * MFFD-NDT-QUALITY-1 — orchestrating service for thermography analysis.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Resolve a {@link FileBundleReference} by appId.</li>
 *   <li>For each {@link ShepardFile} inside the bundle whose filename
 *       extension looks like a TIFF, stream the bytes through ImageIO
 *       (TwelveMonkeys SPI), decode pixel data, hand it to
 *       {@link ThermographyMetrics} for per-frame stats + plate-grid
 *       accumulation, then close the stream.</li>
 *   <li>Persist the bundle-level summary as one
 *       {@code :SemanticAnnotation} per metric under {@code urn:shepard:ndt:*}
 *       on the bundle, plus one DataObject-level {@code quality_score}
 *       annotation (max-across-bundles rule).</li>
 *   <li>Cache the plate-heatmap grid as a separate
 *       {@code urn:shepard:ndt:plate-heatmap-json} annotation on the
 *       bundle so the read-side endpoint can return it without
 *       re-running the analysis.</li>
 * </ul>
 *
 * <p>The advisor's "stream — never accumulate a 6,000-frame stack"
 * rule is enforced by reading-and-discarding each frame inside the
 * {@code for} loop. The plate-grid accumulator is one
 * {@code float[64][64]}; the per-frame stats list is one
 * {@link ThermographyMetrics.FrameStats} record per frame (~80 bytes),
 * so 6,000 frames = ~480 KB of stat residue — well under any concern.
 *
 * <p>{@code sourceMode="ai"} on all derived annotations per the
 * {@code ChannelUnitInferenceService} convention — system-generated,
 * not human-authored, with {@code confidence=1.0} (deterministic
 * computation).
 */
@RequestScoped
public class ThermographyAnalysisService {

  /** Predicate prefix for all thermography-derived NDT annotations. */
  public static final String NDT_PREDICATE_PREFIX = "urn:shepard:ndt:";

  /** Peak hot-spot delta — degrees Celsius above frame median. */
  public static final String PRED_PEAK_DELTA_C = NDT_PREDICATE_PREFIX + "peak-delta-c";

  /** Mean delta — distribution skew indicator. */
  public static final String PRED_MEAN_DELTA_C = NDT_PREDICATE_PREFIX + "mean-delta-c";

  /** Centroid x in frame pixel coordinates. */
  public static final String PRED_HOTSPOT_CENTROID_X = NDT_PREDICATE_PREFIX + "hotspot-centroid-x";

  /** Centroid y in frame pixel coordinates. */
  public static final String PRED_HOTSPOT_CENTROID_Y = NDT_PREDICATE_PREFIX + "hotspot-centroid-y";

  /** Aggregated DataObject-level quality score in [0,1]. */
  public static final String PRED_QUALITY_SCORE = NDT_PREDICATE_PREFIX + "quality-score";

  /** Threshold (degC) used to compute the quality score. */
  public static final String PRED_THRESHOLD_C = NDT_PREDICATE_PREFIX + "threshold-c";

  /** Frame count summarised. */
  public static final String PRED_FRAME_COUNT = NDT_PREDICATE_PREFIX + "frame-count";

  /** Cached plate-heatmap grid as JSON (private predicate, not user-facing). */
  public static final String PRED_PLATE_HEATMAP_JSON = NDT_PREDICATE_PREFIX + "plate-heatmap-json";

  @Inject
  FileBundleReferenceDAO fileBundleReferenceDAO;

  @Inject
  SemanticAnnotationDAO semanticAnnotationDAO;

  @Inject
  FileService fileService;

  /**
   * Deploy default for the quality-score denominator (degrees Celsius).
   * Caller can override per-analysis via the {@code analyze(...)} thresholdC arg;
   * admin can flip globally via the {@code :ThermographyConfig} singleton
   * once that admin-knob lands (deferred — current PR uses application.properties).
   */
  @Inject
  @ConfigProperty(name = "shepard.v2.thermography.threshold-c", defaultValue = "80.0")
  double defaultThresholdC;

  @Inject
  @ConfigProperty(name = "shepard.v2.thermography.grid-width", defaultValue = "64")
  int defaultGridWidth;

  @Inject
  @ConfigProperty(name = "shepard.v2.thermography.grid-height", defaultValue = "64")
  int defaultGridHeight;

  /**
   * Run analysis on the bundle identified by {@code imageBundleAppId}.
   *
   * @throws InvalidBodyException when the bundle cannot be found.
   */
  public AnalyzeResultIO analyze(
    String imageBundleAppId,
    Double thresholdCOverride,
    Integer gridWidthOverride,
    Integer gridHeightOverride
  ) {
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(imageBundleAppId);
    if (bundle == null) {
      throw new InvalidBodyException("FileBundleReference not found for appId=" + imageBundleAppId);
    }

    double thresholdC = (thresholdCOverride != null && thresholdCOverride > 0)
      ? thresholdCOverride : defaultThresholdC;
    int gridW = (gridWidthOverride != null && gridWidthOverride > 0)
      ? gridWidthOverride : defaultGridWidth;
    int gridH = (gridHeightOverride != null && gridHeightOverride > 0)
      ? gridHeightOverride : defaultGridHeight;

    ThermographyMetrics.BundleStats stats = new ThermographyMetrics.BundleStats(gridW, gridH);

    int analyzed = 0;
    int skipped = 0;

    if (bundle.getFileContainer() == null) {
      // Bundle without a backing FileContainer is malformed but tolerable —
      // we just have nothing to analyze. Skip the loop, write the empty result.
      Log.warnf("MFFD-NDT-QUALITY-1: bundle appId=%s has no FileContainer; nothing to analyze",
        imageBundleAppId);
    } else {
      String containerMongoId = bundle.getFileContainer().getMongoId();
      int idx = 0;
      List<ShepardFile> files = bundle.getFiles();
      if (files == null) files = List.of();

      for (ShepardFile f : files) {
        if (!looksLikeTiff(f.getFilename())) {
          // Bundle may carry mixed contents; the parser is best-effort —
          // skip non-TIFF entries quietly, count as skipped for the summary.
          continue;
        }
        NamedInputStream nis = fileService.getPayload(containerMongoId, f.getOid());
        try (InputStream raw = nis.getInputStream()) {
          FrameDecode decoded = decodeTiffFrame(raw);
          if (decoded == null) {
            skipped++;
            Log.warnf("MFFD-NDT-QUALITY-1: skipped non-decodable TIFF %s in bundle %s",
              f.getFilename(), imageBundleAppId);
            continue;
          }
          stats.addFrame(idx, f.getFilename(), decoded.pixels, decoded.width, decoded.height);
          analyzed++;
          idx++;
        } catch (Exception ex) {
          // Per CLAUDE.md, NEVER let one bad frame abort the analysis run.
          skipped++;
          Log.warnf("MFFD-NDT-QUALITY-1: error decoding TIFF %s in bundle %s — %s",
            f.getFilename(), imageBundleAppId, ex.getMessage());
        }
      }
    }

    double maxPeakDelta = stats.maxPeakDeltaC();
    double quality = ThermographyMetrics.qualityScore(maxPeakDelta, thresholdC);
    double meanOfMeanDelta = stats.frames().stream()
      .mapToDouble(ThermographyMetrics.FrameStats::meanDeltaC)
      .average().orElse(0.0);

    int written = persistAnnotations(bundle, stats, thresholdC, quality);

    AnalyzeResultIO result = new AnalyzeResultIO();
    result.setImageBundleAppId(imageBundleAppId);
    result.setFramesAnalyzed(analyzed);
    result.setFramesSkipped(skipped);
    result.setMaxPeakDeltaC(round3(maxPeakDelta));
    result.setMeanOfMeanDeltaC(round3(meanOfMeanDelta));
    result.setMaxC(round3(stats.maxC()));
    result.setThresholdC(thresholdC);
    result.setQualityScore(round3(quality));
    result.setHotspotCentroidX(round3(stats.hotspotCentroidX()));
    result.setHotspotCentroidY(round3(stats.hotspotCentroidY()));
    result.setAnnotationsWritten(written);
    return result;
  }

  /**
   * Read the cached plate-heatmap for {@code imageBundleAppId}.
   * Returns {@code null} when the bundle was never analyzed (so the
   * caller can respond 404 + the UI surfaces the Re-analyze button).
   */
  public PlateHeatmapIO readPlateHeatmap(String imageBundleAppId) {
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(imageBundleAppId);
    if (bundle == null) return null;

    List<SemanticAnnotation> anns = semanticAnnotationDAO.findBySubjectAppId(imageBundleAppId);
    SemanticAnnotation gridAnn = pickByPredicate(anns, PRED_PLATE_HEATMAP_JSON);
    if (gridAnn == null || gridAnn.getValueName() == null) return null;

    Double thresholdC = readNumeric(anns, PRED_THRESHOLD_C);
    Integer frameCount = readInt(anns, PRED_FRAME_COUNT);
    return decodeHeatmapJson(imageBundleAppId, gridAnn.getValueName(),
      thresholdC != null ? thresholdC : defaultThresholdC,
      frameCount != null ? frameCount : 0);
  }

  // ── internal helpers ────────────────────────────────────────────────────

  /** Visible for test — pure name-suffix check, no I/O. */
  public static boolean looksLikeTiff(String filename) {
    if (filename == null) return false;
    String lc = filename.toLowerCase(Locale.ROOT);
    return lc.endsWith(".tif") || lc.endsWith(".tiff");
  }

  /** Stream-decode one TIFF; returns null on any decode failure.
   *  Visible for test — pure static helper, no instance state. */
  public static FrameDecode decodeTiffFrame(InputStream in) {
    try {
      BufferedImage img = ImageIO.read(in);
      if (img == null) return null;
      int w = img.getWidth();
      int h = img.getHeight();
      if (w <= 0 || h <= 0) return null;
      float[] px = new float[w * h];
      Raster raster = img.getRaster();
      int numBands = raster.getNumBands();
      // Single-band greyscale (common for thermal cameras) — read band 0.
      // RGB/RGBA still works: read band 0 (red) as a stand-in. Real radiometric
      // TIFFs are single-band 16-bit; the test fixtures synthesise that shape.
      DataBuffer db = raster.getDataBuffer();
      // dataType drives interpretation: USHORT/SHORT/INT are raw counts; FLOAT
      // is already calibrated. For our pre-calibrated MFFD TIFFs the float
      // path is the contract, but the raw-int path is a useful fallback that
      // keeps the loader from puking on a stray uint16 frame.
      boolean isFloat = db.getDataType() == DataBuffer.TYPE_FLOAT
        || db.getDataType() == DataBuffer.TYPE_DOUBLE;
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          if (isFloat) {
            px[y * w + x] = raster.getSampleFloat(x, y, 0);
          } else if (numBands == 1) {
            px[y * w + x] = raster.getSampleFloat(x, y, 0);
          } else {
            // Average RGB to one float so RGB previews don't crash.
            float sum = 0f;
            for (int b = 0; b < numBands; b++) sum += raster.getSampleFloat(x, y, b);
            px[y * w + x] = sum / numBands;
          }
        }
      }
      return new FrameDecode(px, w, h);
    } catch (Exception ex) {
      return null;
    }
  }

  /** Decoded TIFF — width/height + row-major float pixel array.
   *  Public so test fixtures can construct + inspect outside the package. */
  public static record FrameDecode(float[] pixels, int width, int height) {}

  /**
   * Write or overwrite the derived annotations.
   * sourceMode="ai" + confidence=1.0 per the
   * {@link de.dlr.shepard.context.semantic.services.ChannelUnitInferenceService}
   * convention for system-generated annotations.
   */
  int persistAnnotations(
    FileBundleReference bundle,
    ThermographyMetrics.BundleStats stats,
    double thresholdC,
    double qualityScore
  ) {
    int written = 0;

    // Wipe any existing derived annotations on the bundle first so re-runs
    // don't accumulate stale rows (idempotency rule). We only touch our own
    // urn:shepard:ndt:* predicates — never reach into other vocabularies.
    deleteExistingNdtAnnotations(bundle.getAppId());

    written += writeOnBundle(bundle, PRED_PEAK_DELTA_C, stats.maxPeakDeltaC(),
      "degree Celsius (max delta)");
    written += writeOnBundle(bundle, PRED_MEAN_DELTA_C,
      meanOfMeanDelta(stats), "degree Celsius (mean delta)");
    written += writeOnBundle(bundle, PRED_HOTSPOT_CENTROID_X,
      stats.hotspotCentroidX(), "pixel x");
    written += writeOnBundle(bundle, PRED_HOTSPOT_CENTROID_Y,
      stats.hotspotCentroidY(), "pixel y");
    written += writeOnBundle(bundle, PRED_THRESHOLD_C, thresholdC,
      "threshold in degrees C");
    written += writeOnBundleInt(bundle, PRED_FRAME_COUNT, stats.frameCount());
    written += writeOnBundleText(bundle, PRED_PLATE_HEATMAP_JSON,
      encodeHeatmapJson(stats));

    // DO-level quality score — max across bundles is the conservative pick.
    DataObject parent = bundle.getDataObject();
    if (parent != null) {
      double existing = readExistingDoQualityScore(parent.getAppId());
      double conservative = Math.min(existing, qualityScore);
      // Wipe stale DO-level score before re-writing so we don't accumulate.
      deleteExistingNdtAnnotations(parent.getAppId(), PRED_QUALITY_SCORE);
      written += writeOnDataObject(parent, PRED_QUALITY_SCORE, conservative);
    }
    return written;
  }

  static double meanOfMeanDelta(ThermographyMetrics.BundleStats stats) {
    return stats.frames().stream()
      .mapToDouble(ThermographyMetrics.FrameStats::meanDeltaC)
      .average().orElse(0.0);
  }

  void deleteExistingNdtAnnotations(String subjectAppId) {
    deleteExistingNdtAnnotations(subjectAppId, null);
  }

  void deleteExistingNdtAnnotations(String subjectAppId, String onlyPredicate) {
    List<SemanticAnnotation> existing = semanticAnnotationDAO.findBySubjectAppId(subjectAppId);
    for (SemanticAnnotation ann : existing) {
      String iri = ann.getPropertyIRI();
      if (iri == null) continue;
      if (!iri.startsWith(NDT_PREDICATE_PREFIX)) continue;
      if (onlyPredicate != null && !onlyPredicate.equals(iri)) continue;
      if (ann.getId() != null) {
        semanticAnnotationDAO.deleteByNeo4jId(ann.getId());
      }
    }
  }

  double readExistingDoQualityScore(String dataObjectAppId) {
    List<SemanticAnnotation> anns = semanticAnnotationDAO.findBySubjectAppId(dataObjectAppId);
    SemanticAnnotation hit = pickByPredicate(anns, PRED_QUALITY_SCORE);
    if (hit == null || hit.getNumericValue() == null) return 1.0;  // no prior → permissive
    return hit.getNumericValue();
  }

  int writeOnBundle(VersionableEntity entity, String predicate, double value, String unitLabel) {
    SemanticAnnotation ann = newDerivedAnnotation(entity, predicate);
    ann.setValueName(String.format(Locale.ROOT, "%.4f", value));
    ann.setNumericValue(value);
    if (unitLabel != null) ann.setUnitIRI(unitLabel);
    SemanticAnnotation saved = semanticAnnotationDAO.createOrUpdate(ann);
    entity.addAnnotation(saved);
    return 1;
  }

  int writeOnBundleInt(VersionableEntity entity, String predicate, int value) {
    SemanticAnnotation ann = newDerivedAnnotation(entity, predicate);
    ann.setValueName(Integer.toString(value));
    ann.setNumericValue((double) value);
    SemanticAnnotation saved = semanticAnnotationDAO.createOrUpdate(ann);
    entity.addAnnotation(saved);
    return 1;
  }

  int writeOnBundleText(VersionableEntity entity, String predicate, String value) {
    SemanticAnnotation ann = newDerivedAnnotation(entity, predicate);
    ann.setValueName(value);
    SemanticAnnotation saved = semanticAnnotationDAO.createOrUpdate(ann);
    entity.addAnnotation(saved);
    return 1;
  }

  int writeOnDataObject(DataObject dataObject, String predicate, double value) {
    SemanticAnnotation ann = newDerivedAnnotation(dataObject, predicate);
    ann.setValueName(String.format(Locale.ROOT, "%.4f", value));
    ann.setNumericValue(value);
    SemanticAnnotation saved = semanticAnnotationDAO.createOrUpdate(ann);
    dataObject.addAnnotation(saved);
    return 1;
  }

  SemanticAnnotation newDerivedAnnotation(VersionableEntity entity, String predicate) {
    SemanticAnnotation ann = new SemanticAnnotation();
    ann.setPropertyIRI(predicate);
    ann.setPropertyName(predicate.substring(NDT_PREDICATE_PREFIX.length()));
    ann.setSourceMode("ai");                 // system-generated, per ChannelUnitInferenceService
    ann.setConfidence(1.0);                  // deterministic computation, full confidence
    ann.setVocabularyId(null);               // urn:shepard:ndt:* is an internal namespace
    ann.setSource("thermography-analyze");   // distinguishes from attributes-backfill
    ann.setSubjectKind(entity.getClass().getSimpleName());
    ann.setSubjectAppId(entity.getAppId());
    return ann;
  }

  // ── plate-heatmap JSON codec ────────────────────────────────────────────
  // Hand-rolled to avoid pulling Jackson into the hot path; the format is
  // a flat array of cell rows, joined by newlines, prefixed with a header.
  // Round-trip semantics: encode → decode → original (modulo float precision).

  /** Visible for test — pure string codec, no Neo4j touch. */
  public String encodeHeatmapJson(ThermographyMetrics.BundleStats stats) {
    float[][] grid = stats.plateGridSafe();
    StringBuilder sb = new StringBuilder(grid.length * grid[0].length * 8);
    sb.append("v1:").append(grid.length).append('x').append(grid[0].length).append(':');
    sb.append(stats.minC()).append(':').append(stats.maxC()).append(':');
    for (int y = 0; y < grid.length; y++) {
      for (int x = 0; x < grid[0].length; x++) {
        if (x > 0 || y > 0) sb.append(',');
        sb.append(String.format(Locale.ROOT, "%.3f", grid[y][x]));
      }
    }
    return sb.toString();
  }

  /** Visible for test — pure string codec, no Neo4j touch. */
  public PlateHeatmapIO decodeHeatmapJson(String bundleAppId, String encoded,
                                          double thresholdC, int frameCount) {
    try {
      int colon1 = encoded.indexOf(':');
      int colon2 = encoded.indexOf(':', colon1 + 1);
      int colon3 = encoded.indexOf(':', colon2 + 1);
      int colon4 = encoded.indexOf(':', colon3 + 1);
      if (colon1 < 0 || colon2 < 0 || colon3 < 0 || colon4 < 0) return null;
      String dims = encoded.substring(colon1 + 1, colon2);
      int x = dims.indexOf('x');
      int h = Integer.parseInt(dims.substring(0, x));
      int w = Integer.parseInt(dims.substring(x + 1));
      double minC = Double.parseDouble(encoded.substring(colon2 + 1, colon3));
      double maxC = Double.parseDouble(encoded.substring(colon3 + 1, colon4));
      String[] cellTokens = encoded.substring(colon4 + 1).split(",");
      float[][] cells = new float[h][w];
      for (int y = 0; y < h; y++) {
        for (int xc = 0; xc < w; xc++) {
          cells[y][xc] = Float.parseFloat(cellTokens[y * w + xc]);
        }
      }
      PlateHeatmapIO io = new PlateHeatmapIO();
      io.setImageBundleAppId(bundleAppId);
      io.setWidth(w);
      io.setHeight(h);
      io.setCells(cells);
      io.setMinTemp(minC);
      io.setMaxTemp(maxC);
      io.setThresholdTemp(thresholdC);
      io.setFrameCount(frameCount);
      return io;
    } catch (Exception ex) {
      Log.warnf("MFFD-NDT-QUALITY-1: failed to decode plate-heatmap JSON for %s — %s",
        bundleAppId, ex.getMessage());
      return null;
    }
  }

  static SemanticAnnotation pickByPredicate(List<SemanticAnnotation> anns, String predicateIri) {
    for (SemanticAnnotation a : anns) {
      if (predicateIri.equals(a.getPropertyIRI())) return a;
    }
    return null;
  }

  static Double readNumeric(List<SemanticAnnotation> anns, String predicateIri) {
    SemanticAnnotation hit = pickByPredicate(anns, predicateIri);
    return hit == null ? null : hit.getNumericValue();
  }

  static Integer readInt(List<SemanticAnnotation> anns, String predicateIri) {
    Double v = readNumeric(anns, predicateIri);
    return v == null ? null : v.intValue();
  }

  static double round3(double v) {
    return Math.round(v * 1000.0) / 1000.0;
  }
}
