package de.dlr.shepard.plugin.fileformat.svdx.render;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.references.file.daos.SingletonFileReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.plugin.fileformat.svdx.SvdxAnnotations;
import de.dlr.shepard.spi.view.RenderException;
import de.dlr.shepard.spi.view.RenderRequest;
import de.dlr.shepard.spi.view.RenderResponse;
import de.dlr.shepard.spi.view.RenderResponse.ChannelBindingProjection;
import de.dlr.shepard.spi.view.RenderResponse.ResolvedChannel;
import de.dlr.shepard.spi.view.ViewRecipeRenderer;
import jakarta.enterprise.inject.spi.CDI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * VIEWER-AS-VIEW-RECIPE-RULE-2026-06-29 PR-3 + SVDX-PARSED-CONTENT-SHAPE-2026-06-29.
 *
 * <p>The {@link ViewRecipeRenderer} that consumes the
 * {@code urn:shepard:svdx:*} semantic annotations the
 * {@link de.dlr.shepard.plugin.fileformat.svdx.SvdxManifestParser} emits on
 * every uploaded {@code .svdx} file and projects them as a structured channel
 * catalogue. Sibling — but distinct — to the
 * {@link de.dlr.shepard.v2.svdx.transform.SvdxCsvTransformExecutor}: that one is
 * a {@code MAPPING_RECIPE} that mints a TimeseriesReference from the operator's
 * paired {@code .csv}; this one is a {@code VIEW_RECIPE} that surfaces the
 * channel inventory from the manifest alone, with no {@code .csv} required.
 *
 * <h2>Why this renderer</h2>
 *
 * <p>F-svdx-content (screenshots/F-svdx-content.png) showed an SVDX DataObject
 * landing without surfacing the channels inside the file. The audit performed
 * during PR-3a verified:
 *
 * <ul>
 *   <li>The {@link de.dlr.shepard.plugin.fileformat.svdx.SvdxManifestParser}
 *       trigger is correctly bound to both the legacy {@code POST /v2/files}
 *       and the modern {@code PUT /v2/references/{appId}/content} flows (via
 *       three call-sites in {@code SingletonFileReferenceService}).</li>
 *   <li>The seeded MFFD welding collection's 21 {@code .svdx} FileReferences
 *       already carry ~213 {@code urn:shepard:svdx:*} annotations each — the
 *       parser DID fire on upload; the data is there.</li>
 *   <li>No {@code .csv} siblings exist in that seed, so
 *       {@code SvdxCsvTransformExecutor} can never materialize a
 *       TimeseriesReference there. The channel data is reachable ONLY via the
 *       manifest annotations until SVDX-CSV-SIBLINGS-MISSING-2026-06-29 is
 *       resolved.</li>
 * </ul>
 *
 * <p>This renderer is therefore the only path that can surface the channel
 * catalogue today; once CSV-paired ingest lands, both renderers compose on the
 * same DataObject (catalogue + samples).
 *
 * <h2>Focus resolution</h2>
 *
 * <p>The {@link RenderRequest#focusShepardId()} is probed in two passes:
 *
 * <ol>
 *   <li>If the appId resolves to a {@link FileReference} (a singleton), its own
 *       annotations are read directly.</li>
 *   <li>Otherwise the appId is treated as a {@code DataObject} appId and every
 *       child {@code SingletonFileReference} whose {@code fileKind} ends with
 *       {@code .svdx} (or whose name does) is queried; the renderer concatenates
 *       their channels into one projection so the F-shot scenario (DataObject
 *       landing → child .svdx auto-discovery) works without the user knowing
 *       which appId carries the file.</li>
 * </ol>
 *
 * <h2>Projection shape</h2>
 *
 * <p>Each entry of the returned {@link RenderResponse#channelBindings()} is one
 * channel or ADS-acquisition the manifest enumerated:
 *
 * <pre>
 * {
 *   "role":            "channel-7" | "acquisition-23",
 *   "channelSelector": "{\"channelName\":\"...\",\"symbolName\":\"...\",
 *                        \"dataType\":\"REAL32\",\"amsNetId\":\"...\",
 *                        \"port\":\"851\"}",
 *   "unit":            null,
 *   "required":        false,
 *   "status":          "OK",
 *   "resolved":        { "channelRef": "&lt;.svdx FileReference appId&gt;" }
 * }
 * </pre>
 *
 * <p>The frontend (a Vue component named e.g. {@code SvdxChannelChartView.vue})
 * unpacks the {@code channelSelector} JSON and renders one chart row per entry,
 * grouped per the {@code svdx-ui:groupBy} template-body knob ({@code dataType}
 * by default).
 *
 * <h2>Status posture</h2>
 *
 * <p>Status is {@code OK} when the manifest yielded any channel data, even when
 * the CSV-side TimeseriesReference doesn't yet exist — the catalogue is a
 * standalone deliverable. When no annotations are found (a corrupt or
 * never-parsed file — e.g. the 1/21 in the welding seed without annotations),
 * the projection contains a single {@code MISSING} entry naming the file so the
 * UI can surface "this .svdx couldn't be parsed".
 *
 * <h2>CDI lookup</h2>
 *
 * <p>This class is a plain {@code ServiceLoader} POJO — NOT a CDI bean.
 * Lazy-resolves {@link SemanticAnnotationDAO} + {@link SingletonFileReferenceDAO}
 * via {@link CDI#current()} inside {@link #render(RenderRequest)} — mirrors the
 * {@link de.dlr.shepard.v2.shapes.mffd.MaterialBatchTraceViewRenderer} +
 * {@link de.dlr.shepard.v2.svdx.transform.SvdxCsvTransformExecutor} pattern.
 * Tests bypass the CDI container via
 * {@link #renderWithDaos(RenderRequest, SemanticAnnotationDAO, SingletonFileReferenceDAO)}.
 *
 * <h2>Registration</h2>
 *
 * <p>{@code META-INF/services/de.dlr.shepard.spi.view.ViewRecipeRenderer}.
 */
public final class SvdxChannelChartRenderer implements ViewRecipeRenderer {

  /** The VIEW_RECIPE shape IRI this renderer claims. */
  public static final String SVDX_CHANNEL_CHART_SHAPE_IRI =
    "http://semantics.dlr.de/shepard-ui/svdx#SvdxChannelChartShape";

  /** Frontend renderer hint — the {@code SvdxChannelChartView.vue} component. */
  public static final String RENDERER_HINT = "svdx-channel-chart";

  /** Default cap on projected entries. */
  static final int DEFAULT_MAX_CHANNELS = 200;

  /** Default group-by mode. */
  static final String DEFAULT_GROUP_BY = "dataType";

  static final String STATUS_OK = "OK";
  static final String STATUS_MISSING = "MISSING";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public Set<String> supportedShapeIris() {
    return Set.of(SVDX_CHANNEL_CHART_SHAPE_IRI);
  }

  @Override
  public String name() {
    return "SvdxChannelChartRenderer";
  }

  @Override
  public RenderResponse render(RenderRequest req) {
    SemanticAnnotationDAO annDao;
    SingletonFileReferenceDAO refDao;
    try {
      annDao = CDI.current().select(SemanticAnnotationDAO.class).get();
      refDao = CDI.current().select(SingletonFileReferenceDAO.class).get();
    } catch (RuntimeException ex) {
      throw new RenderException(
        "render.internal-error",
        "SvdxChannelChartRenderer could not resolve DAOs via CDI: " + ex.getMessage(),
        ex
      );
    }
    return renderWithDaos(req, annDao, refDao);
  }

  /**
   * Package-private render entry that takes injected DAOs — used directly by
   * unit tests to bypass the CDI container.
   *
   * @param req    the dispatch request; must not be null
   * @param annDao the semantic-annotation DAO
   * @param refDao the singleton-file-reference DAO
   * @return the projected envelope
   * @throws RenderException when {@code focusShepardId} is null/blank
   */
  RenderResponse renderWithDaos(
    RenderRequest req,
    SemanticAnnotationDAO annDao,
    SingletonFileReferenceDAO refDao
  ) {
    String focusAppId = req == null ? null : req.focusShepardId();
    if (focusAppId == null || focusAppId.isBlank()) {
      throw new RenderException(
        "render.body.invalid",
        "SvdxChannelChartRenderer requires focusShepardId (the .svdx FileReference appId or its parent DataObject appId)"
      );
    }

    BodyKnobs knobs = parseBody(req.templateBodyJson());

    // ─── Resolve focus → list of (fileRefAppId, annotation-set) pairs ──
    List<FocusFile> resolved = resolveFocus(focusAppId, annDao, refDao);

    if (resolved.isEmpty()) {
      // Focus is neither an annotated .svdx FileReference nor a DataObject with
      // an annotated .svdx child. Surface as a MISSING binding so the caller
      // can show a clear "no SVDX manifest found" hint.
      return new RenderResponse(
        req.templateAppId(),
        focusAppId,
        RENDERER_HINT,
        List.of(missingBinding("svdx", focusAppId, "no .svdx manifest annotations found on focus or its children"))
      );
    }

    // ─── Project per-file channels into ChannelBindingProjection entries ──
    List<ChannelBindingProjection> bindings = new ArrayList<>();
    int budget = knobs.maxChannels;
    int channelIdx = 0;
    int acquisitionIdx = 0;

    for (FocusFile ff : resolved) {
      ManifestProjection mp = projectManifest(ff.annotations);
      if (mp.channels.isEmpty() && mp.acquisitions.isEmpty()) {
        bindings.add(missingBinding("svdx-unparsed", ff.fileRefAppId,
          "FileReference has no urn:shepard:svdx:* manifest annotations — possibly an unparseable upload"));
        continue;
      }
      for (ManifestEntry ch : mp.channels) {
        if (budget-- <= 0) break;
        bindings.add(toBinding("channel-" + (channelIdx++), ch, ff.fileRefAppId));
      }
      if (budget > 0 && knobs.showAcquisitions) {
        for (ManifestEntry acq : mp.acquisitions) {
          if (budget-- <= 0) break;
          bindings.add(toBinding("acquisition-" + (acquisitionIdx++), acq, ff.fileRefAppId));
        }
      }
      if (budget <= 0) break;
    }

    if (bindings.isEmpty()) {
      bindings.add(missingBinding("svdx", focusAppId,
        "manifest annotations present but yielded no channels or acquisitions"));
    }

    return new RenderResponse(req.templateAppId(), focusAppId, RENDERER_HINT, bindings);
  }

  // ───────────────────────────────────────────────────────────── focus resolution

  /**
   * Resolve the focus appId to a list of (FileReference appId, annotations) pairs.
   * Probes in order:
   * <ol>
   *   <li>focusAppId == a SingletonFileReference (resolved via DAO) → return its own annotations.</li>
   *   <li>focusAppId == a DataObject → walk children, keep those whose
   *       fileKind/name looks like an .svdx file.</li>
   * </ol>
   */
  private List<FocusFile> resolveFocus(
    String focusAppId,
    SemanticAnnotationDAO annDao,
    SingletonFileReferenceDAO refDao
  ) {
    List<FocusFile> out = new ArrayList<>();

    // Pass 1: is the focus itself a FileReference?
    FileReference asRef = safeFindRef(refDao, focusAppId);
    if (asRef != null) {
      out.add(new FocusFile(focusAppId, annDao.findBySubjectAppId(focusAppId)));
      return out;
    }

    // Pass 2: treat as DataObject, walk children, keep .svdx-looking ones.
    List<FileReference> children = safeFindChildren(refDao, focusAppId);
    for (FileReference child : children) {
      if (child == null) continue;
      if (!looksLikeSvdx(child)) continue;
      String childAppId = child.getAppId();
      if (childAppId == null || childAppId.isBlank()) continue;
      List<SemanticAnnotation> anns = annDao.findBySubjectAppId(childAppId);
      // Even when 0 annotations: still include so the projection surfaces the
      // unparsed-file MISSING entry — gives the UI a hint instead of silently
      // dropping a known .svdx child.
      out.add(new FocusFile(childAppId, anns));
    }
    return out;
  }

  private FileReference safeFindRef(SingletonFileReferenceDAO refDao, String appId) {
    try {
      return refDao.findByAppId(appId);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private List<FileReference> safeFindChildren(SingletonFileReferenceDAO refDao, String dataObjectAppId) {
    try {
      List<FileReference> all = refDao.findByDataObjectAppId(dataObjectAppId);
      return all == null ? List.of() : all;
    } catch (RuntimeException ignored) {
      return List.of();
    }
  }

  private boolean looksLikeSvdx(FileReference ref) {
    String fk = ref.getFileKind();
    if (fk != null && fk.toLowerCase(Locale.ROOT).contains("svdx")) return true;
    String name = ref.getName();
    if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".svdx")) return true;
    return false;
  }

  // ───────────────────────────────────────────────────────────── manifest → entries

  /**
   * Walk the per-FileReference annotation list and reduce it to a list of
   * channel + acquisition manifest entries. Each {@code <Channel>}'s {@code Name}
   * becomes one channel entry; each {@code <AdsAcquisition>}'s
   * {@code <SymbolName>} becomes one acquisition entry. Where the parser
   * preserved order, this projection preserves it via the natural iteration of
   * the annotation list.
   *
   * <p>Top-level metadata (channelCount, dataType list, port list, amsNetId
   * list) is also surfaced — but as an annotated bag on each entry rather than
   * as its own bindings — so the frontend can render "REAL32" / "INT16" badges
   * next to each channel.
   */
  ManifestProjection projectManifest(List<SemanticAnnotation> annotations) {
    ManifestProjection mp = new ManifestProjection();
    if (annotations == null || annotations.isEmpty()) return mp;

    // Distinct top-level pools (preserve insertion order so the projection is
    // deterministic; TreeMap so a missing key surfaces as null vs empty list).
    LinkedHashMap<String, String> chCounts = new LinkedHashMap<>();
    LinkedHashMap<String, String> projectMeta = new LinkedHashMap<>();
    List<String> dataTypes = new ArrayList<>();
    List<String> amsNetIds = new ArrayList<>();
    List<String> ports = new ArrayList<>();
    List<String> channelNames = new ArrayList<>();
    List<String> symbolNames = new ArrayList<>();

    for (SemanticAnnotation a : annotations) {
      if (a == null) continue;
      String iri = a.getPropertyIRI();
      String val = a.getValueName();
      if (iri == null || val == null) continue;
      switch (iri) {
        case SvdxAnnotations.CHANNEL_COUNT -> chCounts.put("channelCount", val);
        case SvdxAnnotations.ACQUISITION_COUNT -> chCounts.put("acquisitionCount", val);
        case SvdxAnnotations.PROJECT_NAME -> projectMeta.put("projectName", val);
        case SvdxAnnotations.PROJECT_GUID -> projectMeta.put("projectGuid", val);
        case SvdxAnnotations.MAIN_SERVER -> projectMeta.put("mainServer", val);
        case SvdxAnnotations.FORMAT_VERSION -> projectMeta.put("formatVersion", val);
        case SvdxAnnotations.RECORD_TIME_NS -> projectMeta.put("recordTimeNs", val);
        case SvdxAnnotations.AUTO_SAVE_MODE -> projectMeta.put("autoSaveMode", val);
        case SvdxAnnotations.ASSEMBLY_NAME -> projectMeta.put("assemblyName", val);
        case SvdxAnnotations.DATA_POOL_GUID -> projectMeta.put("dataPoolGuid", val);
        case SvdxAnnotations.DATA_TYPE -> dataTypes.add(val);
        case SvdxAnnotations.AMS_NET_ID -> amsNetIds.add(val);
        case SvdxAnnotations.PORT -> ports.add(val);
        case SvdxAnnotations.CHANNEL_NAME -> channelNames.add(val);
        case SvdxAnnotations.SYMBOL_NAME -> symbolNames.add(val);
        case SvdxAnnotations.COMPANION_CSV -> projectMeta.put("companionCsvAppId", val);
        default -> { /* ignore unknown */ }
      }
    }

    // The parser emits channelNames and symbolNames as parallel sequences from
    // the manifest. Pair-zip them with the deduplicated AMS/port/dataType pools
    // (which are file-level, not per-channel) — a per-channel binding gets the
    // file-level pool as a multi-value annotation, which is honest about the
    // manifest shape (the binary section's per-channel mapping is undocumented;
    // see SvdxBinaryParser).
    String pooledAms = joinFirst(amsNetIds);
    String pooledPort = joinFirst(ports);
    Map<String, String> sharedMeta = new TreeMap<>();
    sharedMeta.putAll(projectMeta);
    sharedMeta.putAll(chCounts);
    if (!dataTypes.isEmpty()) sharedMeta.put("dataTypes", String.join(",", dataTypes));
    if (!amsNetIds.isEmpty()) sharedMeta.put("amsNetIds", String.join(",", amsNetIds));
    if (!ports.isEmpty()) sharedMeta.put("ports", String.join(",", ports));

    for (int i = 0; i < channelNames.size(); i++) {
      String chName = channelNames.get(i);
      String dt = pickIndexed(dataTypes, i);
      mp.channels.add(new ManifestEntry(chName, null, dt, pooledAms, pooledPort, sharedMeta));
    }
    for (int i = 0; i < symbolNames.size(); i++) {
      String symbol = symbolNames.get(i);
      String dt = pickIndexed(dataTypes, i);
      // Acquisition's short display name lives back-to-back with the symbol in
      // the manifest's <AdsAcquisition>/<Name> child. The parser doesn't emit a
      // separate predicate for it, so we fall back to a derived short form from
      // the symbol (trailing component after the last dot) for human display.
      String shortName = shortName(symbol);
      mp.acquisitions.add(new ManifestEntry(shortName, symbol, dt, pooledAms, pooledPort, sharedMeta));
    }
    return mp;
  }

  private static String pickIndexed(List<String> pool, int i) {
    if (pool == null || pool.isEmpty()) return null;
    return pool.get(i % pool.size());
  }

  private static String joinFirst(List<String> pool) {
    return (pool == null || pool.isEmpty()) ? null : pool.get(0);
  }

  private static String shortName(String fqn) {
    if (fqn == null) return null;
    int dot = fqn.lastIndexOf('.');
    return dot < 0 ? fqn : fqn.substring(dot + 1);
  }

  // ───────────────────────────────────────────────────────────── binding mint

  private ChannelBindingProjection toBinding(String role, ManifestEntry entry, String fileRefAppId) {
    String selector = entry.toSelectorJson();
    return new ChannelBindingProjection(
      role,
      selector,
      /* unit */ null,
      /* required */ false,
      STATUS_OK,
      new ResolvedChannel(fileRefAppId)
    );
  }

  private ChannelBindingProjection missingBinding(String role, String anchorAppId, String reason) {
    Map<String, String> selector = new LinkedHashMap<>();
    selector.put("anchorAppId", anchorAppId == null ? "" : anchorAppId);
    selector.put("reason", reason);
    String selectorJson;
    try {
      selectorJson = MAPPER.writeValueAsString(selector);
    } catch (JsonProcessingException e) {
      selectorJson = "{\"reason\":\"" + reason.replace("\"", "'") + "\"}";
    }
    return new ChannelBindingProjection(role, selectorJson, null, false, STATUS_MISSING, null);
  }

  // ───────────────────────────────────────────────────────────── body knobs

  /** Recipe-body knobs from the VIEW_RECIPE template body. */
  static final class BodyKnobs {
    final String groupBy;
    final boolean showAcquisitions;
    final int maxChannels;

    BodyKnobs(String groupBy, boolean showAcquisitions, int maxChannels) {
      this.groupBy = groupBy;
      this.showAcquisitions = showAcquisitions;
      this.maxChannels = maxChannels;
    }
  }

  BodyKnobs parseBody(String bodyJson) {
    String groupBy = DEFAULT_GROUP_BY;
    boolean showAcquisitions = true;
    int maxChannels = DEFAULT_MAX_CHANNELS;

    if (bodyJson != null && !bodyJson.isBlank()) {
      try {
        JsonNode root = MAPPER.readTree(bodyJson);
        // Both the bare `groupBy` and the namespaced `svdx-ui:groupBy` are
        // accepted — Turtle worked examples use the latter, but a hand-built
        // template body may use the bare form.
        groupBy = textOr(root, "svdx-ui:groupBy", textOr(root, "groupBy", DEFAULT_GROUP_BY));
        showAcquisitions = boolOr(root, "svdx-ui:showAcquisitions",
          boolOr(root, "showAcquisitions", true));
        int parsedMax = intOr(root, "svdx-ui:maxChannels",
          intOr(root, "maxChannels", DEFAULT_MAX_CHANNELS));
        if (parsedMax > 0) maxChannels = parsedMax;
      } catch (JsonProcessingException ignored) {
        // Malformed body — defaults preserved.
      }
    }
    return new BodyKnobs(groupBy, showAcquisitions, maxChannels);
  }

  private static String textOr(JsonNode node, String field, String fallback) {
    JsonNode v = node.path(field);
    return (v != null && v.isTextual() && !v.asText().isBlank()) ? v.asText() : fallback;
  }

  private static boolean boolOr(JsonNode node, String field, boolean fallback) {
    JsonNode v = node.path(field);
    return (v != null && v.isBoolean()) ? v.asBoolean() : fallback;
  }

  private static int intOr(JsonNode node, String field, int fallback) {
    JsonNode v = node.path(field);
    if (v == null) return fallback;
    if (v.isInt()) return v.asInt();
    if (v.isLong()) return (int) Math.min(Integer.MAX_VALUE, v.asLong());
    if (v.isTextual()) {
      try {
        return Integer.parseInt(v.asText());
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  // ───────────────────────────────────────────────────────────── scratch records

  /** A focus FileReference plus its loaded annotation set. */
  static final class FocusFile {
    final String fileRefAppId;
    final List<SemanticAnnotation> annotations;

    FocusFile(String fileRefAppId, List<SemanticAnnotation> annotations) {
      this.fileRefAppId = fileRefAppId;
      this.annotations = annotations == null ? List.of() : annotations;
    }
  }

  /** The two-bag projection of a single .svdx manifest. */
  static final class ManifestProjection {
    final List<ManifestEntry> channels = new ArrayList<>();
    final List<ManifestEntry> acquisitions = new ArrayList<>();
  }

  /**
   * One projected manifest entry. {@code channelName} and {@code symbolName}
   * are mutually optional — a channel from the {@code <Channel>} bag carries
   * only its display name; an acquisition from the {@code <AdsAcquisition>} bag
   * carries the fully qualified symbol path.
   */
  static final class ManifestEntry {
    final String channelName;
    final String symbolName;
    final String dataType;
    final String amsNetId;
    final String port;
    final Map<String, String> shared;

    ManifestEntry(String channelName, String symbolName, String dataType,
                  String amsNetId, String port, Map<String, String> shared) {
      this.channelName = channelName;
      this.symbolName = symbolName;
      this.dataType = dataType;
      this.amsNetId = amsNetId;
      this.port = port;
      this.shared = shared == null ? Map.of() : shared;
    }

    /** JSON form the frontend reads to render one chart row. */
    String toSelectorJson() {
      Map<String, Object> sel = new LinkedHashMap<>();
      if (channelName != null) sel.put("channelName", channelName);
      if (symbolName != null) sel.put("symbolName", symbolName);
      if (dataType != null) sel.put("dataType", dataType);
      if (amsNetId != null) sel.put("amsNetId", amsNetId);
      if (port != null) sel.put("port", port);
      if (!shared.isEmpty()) sel.put("manifest", shared);
      try {
        return MAPPER.writeValueAsString(sel);
      } catch (JsonProcessingException e) {
        return "{}";
      }
    }
  }
}
