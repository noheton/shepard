package de.dlr.shepard.context.semantic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.eclipse.microprofile.config.ConfigProvider;
import org.neo4j.ogm.session.Session;

/**
 * N1b (+ ONT1a + ONT1b) — seeds shepard's internal neosemantics ("n10s")
 * repository with the ten bundled common ontologies (PROV-O / Dublin
 * Core / schema.org / FOAF / QUDT / OM-2 / W3C Time / GeoSPARQL / OBO
 * Relation Ontology / NFDI4Ing metadata4ing).
 *
 * <p>The service:
 * <ol>
 *   <li>Reads {@code /ontologies/ontologies-manifest.json} from the
 *       classpath.</li>
 *   <li>For each enabled entry: loads the bundled {@code .ttl} from
 *       the classpath, verifies its SHA-256 matches the manifest, and
 *       invokes {@code CALL n10s.rdf.import.inline(...)} on the
 *       shared OGM session.</li>
 *   <li>Records progress in the log; cumulative {@code :Resource}
 *       count is logged after each successful import.</li>
 * </ol>
 *
 * <p><b>Idempotent.</b> n10s deduplicates by IRI via the
 * {@code n10s_unique_uri} constraint (created in
 * {@link N10sBootstrapHook}); a re-run of {@link #seedIfNeeded()}
 * sees zero <i>new</i> triples and logs that explicitly. We don't
 * try to short-circuit "already imported" — n10s's own dedup is fast
 * enough, and re-running on every startup is the simplest way to
 * recover from an admin who hand-deleted ontology nodes.
 *
 * <p><b>Fail-soft.</b> If n10s is absent, if a bundle is missing /
 * hash-mismatched, or if {@code n10s.rdf.import.inline} returns a
 * non-OK row, the per-ontology error is logged at WARN and the next
 * ontology is attempted. The service never throws — the upstream
 * design (see {@code aidocs/48}) is that {@code INTERNAL} repos are a
 * convenience layer; their absence is an annotation-resolution
 * degradation, never a startup gate.
 *
 * @see N10sBootstrapHook
 * @see InternalSemanticConnector
 */
public class OntologySeedService {

  /** Config key — master toggle. Default ON; flip to false for a bare-n10s install. */
  public static final String ENABLED_PROPERTY = "shepard.semantic.internal.preseed-ontologies.enabled";

  /** Config key — CSV of bundle {@code id}s to skip (e.g. {@code "qudt,om-2"}). Default empty. */
  public static final String SKIP_BUNDLES_PROPERTY = "shepard.semantic.internal.preseed-ontologies.skip-bundles";

  /**
   * Config key (N1c2) — filesystem directory under which the
   * operator-uploaded user bundles + their on-disk TTL files live.
   * Default {@code /var/lib/shepard/ontologies/}. Deploy-time only
   * per the {@code aidocs/65 §2.3} exception (filesystem topology).
   */
  public static final String USER_BUNDLES_DIR_PROPERTY = "shepard.semantic.internal.user-bundles-dir";

  /** Default filesystem location for operator-uploaded bundle TTLs. */
  public static final String DEFAULT_USER_BUNDLES_DIR = "/var/lib/shepard/ontologies/";

  /** Classpath location of the manifest. */
  static final String MANIFEST_RESOURCE = "/ontologies/ontologies-manifest.json";

  /** Classpath prefix for the bundled TTL files (sibling of the manifest). */
  static final String ONTOLOGY_RESOURCE_PREFIX = "/ontologies/";

  /**
   * The seed import call. n10s doesn't accept {@code classpath:} URIs
   * in {@code n10s.rdf.import.fetch}, so we read the bundle into a
   * String and pass it via the inline variant. {@code commitSize} is
   * sized generously — our bundled stubs are kilobytes, not megabytes.
   */
  static final String IMPORT_INLINE_CYPHER =
    "CALL n10s.rdf.import.inline($rdf, $format) " +
    "YIELD terminationStatus, triplesLoaded, triplesParsed, namespaces, extraInfo " +
    "RETURN terminationStatus AS status, triplesLoaded AS loaded, triplesParsed AS parsed, extraInfo AS info";

  /** Cypher for the cumulative {@code :Resource} count emitted per-ontology + at end. */
  static final String RESOURCE_COUNT_CYPHER = "MATCH (r:Resource) RETURN count(r) AS total";

  /** Probe to detect n10s presence (same shape as N10sBootstrapHook's). */
  static final String DETECT_CYPHER =
    "CALL dbms.procedures() YIELD name WHERE name STARTS WITH 'n10s.' " +
    "RETURN count(name) > 0 AS available";

  private final Session session;
  private final boolean enabled;
  private final Set<String> skipBundles;
  private final ObjectMapper objectMapper;
  private final ClassLoader classLoader;

  /**
   * N1c2 — supplier of runtime-overridable preseed knobs. Re-read on
   * every {@link #seedIfNeeded()} invocation so post-startup admin
   * mutations land on the next seed pass. Production injects the
   * Neo4j-backed {@code :SemanticConfig} reader via {@code OntologyConfigService};
   * pre-N1c2 tests inject {@link RuntimeConfig#deployTimeOnly()} for
   * the legacy "no runtime config" behaviour.
   */
  private final java.util.function.Supplier<RuntimeConfig> runtimeConfigSupplier;

  /**
   * N1c2 — supplier of operator-uploaded {@link OntologyEntry}
   * records. Production injects the Neo4j-backed
   * {@code OntologyConfigService.listUserEntries()}; pre-N1c2 tests
   * supply {@code List::of}.
   */
  private final java.util.function.Supplier<List<OntologyEntry>> userEntriesSupplier;

  /**
   * Production ctor — pulls the OGM session at call time and wires
   * the runtime-config + user-entries suppliers via a freshly
   * instantiated {@link de.dlr.shepard.context.semantic.services.OntologyConfigService}.
   * The service is request-scoped at the CDI layer but its DAOs work
   * outside a request when manually constructed (every DAO pulls the
   * OGM session via {@code NeoConnector.getInstance()} on its no-arg
   * ctor), so the startup-hook code path is supported.
   */
  public OntologySeedService() {
    this(
      de.dlr.shepard.common.neo4j.NeoConnector.getInstance().getNeo4jSession(),
      readBooleanConfig(ENABLED_PROPERTY, true),
      parseSkipBundles(readStringConfig(SKIP_BUNDLES_PROPERTY, "")),
      new ObjectMapper(),
      OntologySeedService.class.getClassLoader(),
      () -> productionConfigService().loadRuntimeConfig(),
      () -> productionConfigService().listUserEntries()
    );
  }

  /**
   * Construct a production-ready {@link
   * de.dlr.shepard.context.semantic.services.OntologyConfigService}
   * with manually-wired DAOs. Used by the no-arg ctor above for the
   * startup-hook code path. Visible-for-tests-package-private so
   * tests can replace the production wiring with a mock.
   */
  static de.dlr.shepard.context.semantic.services.OntologyConfigService productionConfigService() {
    de.dlr.shepard.context.semantic.daos.SemanticConfigDAO cfgDao =
      new de.dlr.shepard.context.semantic.daos.SemanticConfigDAO();
    de.dlr.shepard.context.semantic.daos.UserOntologyBundleDAO userDao =
      new de.dlr.shepard.context.semantic.daos.UserOntologyBundleDAO();
    return new de.dlr.shepard.context.semantic.services.OntologyConfigService(cfgDao, userDao);
  }

  /** Pre-N1c2 test seam — accept session + config + classloader injection. */
  public OntologySeedService(
    Session session,
    boolean enabled,
    Set<String> skipBundles,
    ObjectMapper objectMapper,
    ClassLoader classLoader
  ) {
    this(session, enabled, skipBundles, objectMapper, classLoader, RuntimeConfig::deployTimeOnly, List::of);
  }

  /**
   * N1c2 test seam — accept session + deploy-time config + classloader
   * + a runtime-config supplier + a user-entries supplier. Production
   * code wires the latter two from {@code OntologyConfigService}; tests
   * pass closures.
   */
  public OntologySeedService(
    Session session,
    boolean enabled,
    Set<String> skipBundles,
    ObjectMapper objectMapper,
    ClassLoader classLoader,
    java.util.function.Supplier<RuntimeConfig> runtimeConfigSupplier,
    java.util.function.Supplier<List<OntologyEntry>> userEntriesSupplier
  ) {
    this.session = session;
    this.enabled = enabled;
    this.skipBundles = skipBundles == null ? Collections.emptySet() : skipBundles;
    this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    this.classLoader = classLoader == null ? OntologySeedService.class.getClassLoader() : classLoader;
    this.runtimeConfigSupplier = runtimeConfigSupplier == null ? RuntimeConfig::deployTimeOnly : runtimeConfigSupplier;
    this.userEntriesSupplier = userEntriesSupplier == null ? List::of : userEntriesSupplier;
  }

  /**
   * Run the seed import. Idempotent — safe to call on every startup.
   * Never throws on ontology-related errors; logs at WARN and proceeds.
   *
   * <p><b>Precedence (post-N1c2).</b>
   * <ol>
   *   <li><b>Master enable.</b> Runtime
   *       {@link RuntimeConfig#preseedEnabled} wins over the
   *       deploy-time {@link #ENABLED_PROPERTY}. When either is
   *       {@code false}, the entire pass is a no-op — except
   *       required bundles, which are always seeded.</li>
   *   <li><b>Required bundles always seed.</b> A manifest entry
   *       with {@code required: true} bypasses every disable
   *       check; the only way to skip it is to remove the entry
   *       from the manifest entirely.</li>
   *   <li><b>Per-bundle disable.</b> A bundle id in the runtime
   *       {@link RuntimeConfig#disabledBundles} set OR in the
   *       deploy-time {@code skip-bundles} CSV is skipped.</li>
   * </ol>
   *
   * <p>Built-in bundles are processed first (manifest declaration
   * order), then user-uploaded bundles ({@code bundleId} ASC), so
   * cross-references resolve correctly.
   */
  public void seedIfNeeded() {
    RuntimeConfig runtime = runtimeConfigSupplier.get();
    if (runtime == null) runtime = RuntimeConfig.deployTimeOnly();
    boolean masterEnabled = enabled && runtime.preseedEnabled();

    if (session == null) {
      Log.warn("OntologySeedService: no OGM session available; skipping pre-seed.");
      return;
    }

    if (!detectN10s()) {
      Log.warn(
        "OntologySeedService: neosemantics (n10s) procedures not registered. " +
        "Skipping ontology pre-seed; INTERNAL repositories will resolve only against whatever the operator imports manually."
      );
      return;
    }

    final List<OntologyEntry> entries;
    try {
      entries = loadAllEntries();
    } catch (RuntimeException ex) {
      Log.warnf(
        "OntologySeedService: failed to load manifest %s (%s: %s); skipping pre-seed entirely.",
        MANIFEST_RESOURCE,
        ex.getClass().getSimpleName(),
        ex.getMessage()
      );
      return;
    }

    if (entries.isEmpty()) {
      Log.info("OntologySeedService: manifest has zero entries; nothing to seed.");
      return;
    }

    if (!masterEnabled) {
      // Master-off — required bundles still seed; everything else
      // is skipped without complaint. This is the
      // "I want a bare n10s" path that still has PROV-O for audit.
      Log.infof(
        "OntologySeedService: master toggle off (deploy=%b runtime=%b); seeding required bundles only.",
        enabled,
        runtime.preseedEnabled()
      );
    }

    int seeded = 0;
    int skipped = 0;
    int failed = 0;
    long beforeTotal = readResourceCount();
    for (OntologyEntry entry : entries) {
      SeedDecision decision = shouldSeed(entry, masterEnabled, runtime);
      if (decision == SeedDecision.SKIP_DISABLED) {
        Log.infof("OntologySeedService: bundle '%s' admin-disabled; skipping.", entry.id);
        skipped++;
        continue;
      }
      if (decision == SeedDecision.SKIP_MASTER_OFF) {
        Log.infof(
          "OntologySeedService: bundle '%s' skipped (master toggle off, not required).",
          entry.id
        );
        skipped++;
        continue;
      }
      try {
        seedOne(entry);
        seeded++;
      } catch (RuntimeException ex) {
        Log.warnf(
          "OntologySeedService: bundle '%s' failed (%s: %s); continuing with next.",
          entry.id,
          ex.getClass().getSimpleName(),
          ex.getMessage()
        );
        failed++;
      }
    }

    long afterTotal = readResourceCount();
    long delta = afterTotal - beforeTotal;
    Log.infof(
      "OntologySeedService: pre-seed complete — seeded=%d skipped=%d failed=%d; :Resource count %d -> %d (+%d new).",
      seeded,
      skipped,
      failed,
      beforeTotal,
      afterTotal,
      Math.max(0L, delta)
    );
  }

  /**
   * Decision for one bundle on one pass. Visible-for-tests so the
   * precedence rules can be asserted directly without standing up
   * the full seed loop.
   */
  enum SeedDecision {
    /** Bundle seeds (required-true, or master-on and not in any disable set). */
    SEED,
    /** Bundle is in the runtime or deploy-time disable set. */
    SKIP_DISABLED,
    /** Master toggle is off and bundle is not required. */
    SKIP_MASTER_OFF,
  }

  /**
   * Apply the precedence rules. Visible-for-tests.
   *
   * <p>Order (each row returns immediately):
   * <ol>
   *   <li>{@code required=true} → {@link SeedDecision#SEED}.</li>
   *   <li>master off → {@link SeedDecision#SKIP_MASTER_OFF}.</li>
   *   <li>id in {@code runtime.disabledBundles ∪ skipBundles} →
   *       {@link SeedDecision#SKIP_DISABLED}.</li>
   *   <li>otherwise → {@link SeedDecision#SEED}.</li>
   * </ol>
   */
  SeedDecision shouldSeed(OntologyEntry entry, boolean masterEnabled, RuntimeConfig runtime) {
    if (entry.required) return SeedDecision.SEED;
    if (!masterEnabled) return SeedDecision.SKIP_MASTER_OFF;
    Set<String> runtimeDisabled = runtime == null ? Collections.emptySet() : runtime.disabledBundles();
    if (runtimeDisabled.contains(entry.id) || skipBundles.contains(entry.id)) {
      return SeedDecision.SKIP_DISABLED;
    }
    return SeedDecision.SEED;
  }

  /**
   * Combine the classpath-manifest built-ins with the user-uploaded
   * entries (from the supplier injected at construction). Order is
   * stable: built-ins first (manifest declaration order), then user
   * uploads in the supplier's order.
   */
  List<OntologyEntry> loadAllEntries() {
    List<OntologyEntry> builtins = loadManifest();
    List<OntologyEntry> userEntries = userEntriesSupplier.get();
    if (userEntries == null || userEntries.isEmpty()) {
      return builtins;
    }
    Set<String> builtinIds = new java.util.LinkedHashSet<>(builtins.size());
    for (OntologyEntry b : builtins) builtinIds.add(b.id);
    List<OntologyEntry> out = new ArrayList<>(builtins.size() + userEntries.size());
    out.addAll(builtins);
    for (OntologyEntry u : userEntries) {
      if (u == null) continue;
      if (builtinIds.contains(u.id)) {
        // Defence-in-depth: the upload endpoint refuses a user
        // bundle whose id collides with a built-in. If the data
        // ended up here anyway (manual DB tinkering, for example),
        // we log + drop the user entry rather than double-seed.
        Log.warnf(
          "OntologySeedService: user bundle '%s' shadows a built-in id; skipping the user entry.",
          u.id
        );
        continue;
      }
      out.add(u);
    }
    return out;
  }

  /**
   * Import one ontology: verify SHA-256, call n10s, log progress.
   * Throws on hash mismatch / missing file / n10s call error so the
   * outer loop can route to the per-bundle WARN path.
   */
  void seedOne(OntologyEntry entry) {
    byte[] bytes = readBundleBytes(entry.file);
    String actualSha = sha256Hex(bytes);
    if (!actualSha.equalsIgnoreCase(entry.sha256)) {
      throw new OntologySeedException(
        "SHA-256 mismatch for bundle '" + entry.id + "' (file=" + entry.file +
        "): manifest declares " + entry.sha256 + " but classpath file hashes to " + actualSha +
        ". Either restore the bundled file or regenerate the manifest entry."
      );
    }
    if (bytes.length != entry.sizeBytes) {
      Log.warnf(
        "OntologySeedService: bundle '%s' size differs (manifest=%d, actual=%d); SHA matched so proceeding.",
        entry.id,
        entry.sizeBytes,
        bytes.length
      );
    }
    String rdf = new String(bytes, StandardCharsets.UTF_8);
    Map<String, Object> result = invokeImport(rdf, entry.format);
    Object status = result.get("status");
    Object loaded = result.get("loaded");
    long loadedCount = toLong(loaded);
    Log.infof(
      "OntologySeedService: bundle '%s' imported — status=%s triplesLoaded=%s (cumulative :Resource=%d)",
      entry.id,
      status,
      loaded,
      readResourceCount()
    );
    if (status != null && !"OK".equalsIgnoreCase(status.toString())) {
      // n10s emits "OK" for happy-path imports. On repeat runs the
      // constraint guard quietly absorbs duplicates and n10s still
      // returns "OK" with triplesLoaded == 0 — that's the idempotent
      // case, NOT an error. Anything else (KO / partial / failed) is
      // logged but not raised; the next ontology still gets a shot.
      Log.warnf(
        "OntologySeedService: bundle '%s' returned non-OK status '%s' (loaded=%d). Continuing.",
        entry.id,
        status,
        loadedCount
      );
    }
  }

  /**
   * Invoke {@code n10s.rdf.import.inline} with the given RDF + format.
   * Visible-for-tests so the assertion on call composition is direct.
   */
  Map<String, Object> invokeImport(String rdf, String format) {
    var result = session.query(IMPORT_INLINE_CYPHER, Map.of("rdf", rdf, "format", format));
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return Collections.emptyMap();
    return new java.util.LinkedHashMap<>(it.next());
  }

  /** Total {@code :Resource} count, used for progress logging. Returns 0 on probe failure. */
  long readResourceCount() {
    try {
      var result = session.query(RESOURCE_COUNT_CYPHER, Collections.emptyMap());
      var it = result.queryResults().iterator();
      if (!it.hasNext()) return 0;
      return toLong(it.next().get("total"));
    } catch (RuntimeException ex) {
      Log.warnf("OntologySeedService: :Resource count probe failed (%s).", ex.getClass().getSimpleName());
      return 0;
    }
  }

  /**
   * Read the manifest from classpath. Visible-for-tests and to the
   * N1c2 admin REST layer (which needs the built-in `required` flags
   * to render the merged list / refuse disable on required bundles).
   */
  public List<OntologyEntry> loadManifest() {
    InputStream in = classLoader.getResourceAsStream(stripLeadingSlash(MANIFEST_RESOURCE));
    if (in == null) {
      throw new OntologySeedException("Classpath resource " + MANIFEST_RESOURCE + " not found.");
    }
    final JsonNode root;
    try (InputStream s = in) {
      root = objectMapper.readTree(s);
    } catch (IOException ex) {
      throw new OntologySeedException("Failed to parse " + MANIFEST_RESOURCE + ": " + ex.getMessage(), ex);
    }
    JsonNode arr = root.get("ontologies");
    if (arr == null || !arr.isArray()) {
      throw new OntologySeedException("Manifest " + MANIFEST_RESOURCE + " missing 'ontologies' array.");
    }
    List<OntologyEntry> out = new ArrayList<>(arr.size());
    Set<String> seenIds = new HashSet<>();
    for (JsonNode n : arr) {
      OntologyEntry e = OntologyEntry.fromJson(n);
      if (!seenIds.add(e.id)) {
        throw new OntologySeedException("Duplicate ontology id '" + e.id + "' in manifest.");
      }
      out.add(e);
    }
    return out;
  }

  /** Read a bundled ontology file from classpath as bytes. */
  byte[] readBundleBytes(String fileName) {
    String resourcePath = ONTOLOGY_RESOURCE_PREFIX + fileName;
    InputStream in = classLoader.getResourceAsStream(stripLeadingSlash(resourcePath));
    if (in == null) {
      throw new OntologySeedException("Classpath resource " + resourcePath + " not found.");
    }
    try (InputStream s = in; ByteArrayOutputStream buf = new ByteArrayOutputStream(8192)) {
      byte[] chunk = new byte[4096];
      int read;
      while ((read = s.read(chunk)) >= 0) {
        buf.write(chunk, 0, read);
      }
      return buf.toByteArray();
    } catch (IOException ex) {
      throw new OntologySeedException("Failed to read " + resourcePath + ": " + ex.getMessage(), ex);
    }
  }

  /** Hex SHA-256 of the given bytes. */
  static String sha256Hex(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(bytes);
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(String.format(Locale.ROOT, "%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException ex) {
      // SHA-256 is mandated by the JRE spec; this branch is unreachable.
      throw new OntologySeedException("SHA-256 unavailable: " + ex.getMessage(), ex);
    }
  }

  private boolean detectN10s() {
    try {
      var result = session.query(DETECT_CYPHER, Collections.emptyMap());
      var it = result.queryResults().iterator();
      if (!it.hasNext()) return false;
      return Boolean.TRUE.equals(it.next().get("available"));
    } catch (RuntimeException ex) {
      Log.warnf("OntologySeedService: detection probe raised %s; treating n10s as absent.", ex.getClass().getSimpleName());
      return false;
    }
  }

  private static String stripLeadingSlash(String s) {
    return s.startsWith("/") ? s.substring(1) : s;
  }

  private static long toLong(Object raw) {
    if (raw instanceof Number n) return n.longValue();
    if (raw == null) return 0;
    try {
      return Long.parseLong(raw.toString());
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  /**
   * Visible-to-the-N1c2 config service so the merged-listing endpoint
   * can compute the union of deploy-time skip-bundles + runtime
   * disabledBundles.
   */
  public static Set<String> parseSkipBundles(String csv) {
    if (csv == null || csv.isBlank()) return Collections.emptySet();
    Set<String> out = new LinkedHashSet<>();
    for (String part : csv.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) out.add(trimmed);
    }
    return Collections.unmodifiableSet(out);
  }

  private static boolean readBooleanConfig(String key, boolean fallback) {
    try {
      return ConfigProvider.getConfig().getOptionalValue(key, Boolean.class).orElse(fallback);
    } catch (RuntimeException ex) {
      return fallback;
    }
  }

  private static String readStringConfig(String key, String fallback) {
    try {
      return ConfigProvider.getConfig().getOptionalValue(key, String.class).orElse(fallback);
    } catch (RuntimeException ex) {
      return fallback;
    }
  }

  /**
   * Manifest row — visible-for-tests so the sanity test can inspect it.
   *
   * <p>Post-N1c2 carries two new fields:
   * <ul>
   *   <li>{@code required} — when {@code true}, the bundle is seeded
   *       unconditionally even if the operator names it in
   *       {@code disabledBundles} or the deploy-time
   *       {@code skip-bundles} CSV. Required wins.</li>
   *   <li>{@code source} — {@link Source#BUILTIN} for classpath
   *       bundles shipped in the JAR; {@link Source#USER} for
   *       operator-uploaded bundles managed by
   *       {@code OntologyConfigService}.</li>
   * </ul>
   *
   * <p>{@code file} is the on-disk path the seed service reads
   * bytes from. For built-ins this is a classpath-relative slug
   * ({@code "prov-o.ttl"} → {@code "/ontologies/prov-o.ttl"}); for
   * user uploads it's an absolute filesystem path.
   */
  public static final class OntologyEntry {

    /** Origin of the bundle — classpath ("builtin") or operator upload ("user"). */
    public enum Source {
      BUILTIN,
      USER,
    }

    public final String id;
    public final String name;
    public final String file;
    public final String iriPrefix;
    public final String format;
    public final String canonicalUrl;
    public final String license;
    public final String version;
    public final String sha256;
    public final long sizeBytes;
    public final boolean required;
    public final Source source;

    public OntologyEntry(
      String id,
      String name,
      String file,
      String iriPrefix,
      String format,
      String canonicalUrl,
      String license,
      String version,
      String sha256,
      long sizeBytes
    ) {
      this(id, name, file, iriPrefix, format, canonicalUrl, license, version, sha256, sizeBytes, false, Source.BUILTIN);
    }

    public OntologyEntry(
      String id,
      String name,
      String file,
      String iriPrefix,
      String format,
      String canonicalUrl,
      String license,
      String version,
      String sha256,
      long sizeBytes,
      boolean required,
      Source source
    ) {
      this.id = Objects.requireNonNull(id, "id");
      this.name = name;
      this.file = Objects.requireNonNull(file, "file");
      this.iriPrefix = iriPrefix;
      this.format = format == null || format.isBlank() ? "Turtle" : format;
      this.canonicalUrl = canonicalUrl;
      this.license = license;
      this.version = version;
      this.sha256 = Objects.requireNonNull(sha256, "sha256");
      this.sizeBytes = sizeBytes;
      this.required = required;
      this.source = source == null ? Source.BUILTIN : source;
    }

    static OntologyEntry fromJson(JsonNode n) {
      String id = requireString(n, "id");
      String file = requireString(n, "file");
      String sha = requireString(n, "sha256");
      if (sha.length() != 64 || !sha.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
        throw new OntologySeedException(
          "Manifest entry '" + id + "' has invalid sha256 '" + sha + "' (expected 64 hex chars)."
        );
      }
      return new OntologyEntry(
        id,
        textOrNull(n, "name"),
        file,
        textOrNull(n, "iriPrefix"),
        textOrNull(n, "format"),
        textOrNull(n, "canonicalUrl"),
        textOrNull(n, "license"),
        textOrNull(n, "version"),
        sha,
        n.path("sizeBytes").asLong(-1L),
        n.path("required").asBoolean(false),
        Source.BUILTIN
      );
    }

    private static String requireString(JsonNode n, String field) {
      JsonNode v = n.get(field);
      if (v == null || !v.isTextual() || v.asText().isBlank()) {
        throw new OntologySeedException("Manifest entry missing required field '" + field + "'.");
      }
      return v.asText();
    }

    private static String textOrNull(JsonNode n, String field) {
      JsonNode v = n.get(field);
      if (v == null || v.isNull()) return null;
      return v.isTextual() ? v.asText() : v.toString();
    }
  }

  /** Internal seeding exception type — never escapes {@link #seedIfNeeded()}. */
  static final class OntologySeedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    OntologySeedException(String message) {
      super(message);
    }

    OntologySeedException(String message, Throwable cause) {
      super(message, cause);
    }
  }

}
