package de.dlr.shepard.context.semantic.services;

import de.dlr.shepard.context.semantic.OntologySeedService;
import de.dlr.shepard.context.semantic.OntologySeedService.OntologyEntry;
import de.dlr.shepard.context.semantic.RuntimeConfig;
import de.dlr.shepard.context.semantic.daos.SemanticConfigDAO;
import de.dlr.shepard.context.semantic.daos.UserOntologyBundleDAO;
import de.dlr.shepard.context.semantic.entities.SemanticConfig;
import de.dlr.shepard.context.semantic.entities.UserOntologyBundle;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * N1c2 — the service that fronts the {@code :SemanticConfig} singleton
 * and the {@code :UserOntologyBundle} catalogue (per
 * {@code aidocs/65 §2.2 + §2.3}).
 *
 * <p>Owners:
 * <ul>
 *   <li>Reading the {@code preseedEnabled / disabledBundles} runtime
 *       knobs (with first-start seeding from deploy-time defaults).</li>
 *   <li>Enable / disable a single bundle — refusing the disable when
 *       the manifest entry is {@code required: true}.</li>
 *   <li>Upload a new operator-supplied {@code .ttl} — write bytes,
 *       SHA-256, persist the catalogue row, and refuse on id collision
 *       or oversize / malformed payload.</li>
 *   <li>Remove a user-uploaded bundle — refuse on built-in id.</li>
 *   <li>Project the merged built-in + user view for
 *       {@code SemanticAdminRest.list}.</li>
 * </ul>
 *
 * <p>The {@link OntologySeedService} is the consumer — on every startup
 * it pulls {@link #loadRuntimeConfig()} + {@link #listUserEntries()}
 * via the suppliers wired into its ctor, and applies the precedence
 * rules from {@code aidocs/65 §2.6}.
 *
 * <p>RFC 7807 problem codes used by callers:
 * <ul>
 *   <li>{@code semantic.bundle.not-found} (404)</li>
 *   <li>{@code semantic.bundle.required} (409) — disable attempt on
 *       a {@code required: true} bundle</li>
 *   <li>{@code semantic.bundle.duplicate-id} (409) — upload id
 *       collides with built-in or another user bundle</li>
 *   <li>{@code semantic.bundle.builtin-not-removable} (409) — delete
 *       attempt against a built-in</li>
 *   <li>{@code semantic.bundle.invalid-ttl} (400)</li>
 *   <li>{@code semantic.bundle.too-large} (400) — payload > 10 MB</li>
 *   <li>{@code semantic.bundle.bad-metadata} (400) — missing or
 *       malformed metadata fields on upload</li>
 * </ul>
 *
 * <p>Cited rules: CLAUDE.md "Always: surface operator knobs in the
 * admin config" + {@code aidocs/65}.
 */
@RequestScoped
public class OntologyConfigService {

  /** Hard upload cap (bytes). 10 MB is generous for any real-world Turtle ontology. */
  static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;

  /** Bundle-id slug constraint. ASCII + dashes + underscores; ≤ 64 chars. */
  static final Pattern BUNDLE_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");

  @Inject
  SemanticConfigDAO configDAO;

  @Inject
  UserOntologyBundleDAO userBundleDAO;

  /** Production no-arg ctor for CDI. */
  public OntologyConfigService() {}

  /** Test-seam ctor — inject DAOs directly. */
  public OntologyConfigService(SemanticConfigDAO configDAO, UserOntologyBundleDAO userBundleDAO) {
    this.configDAO = configDAO;
    this.userBundleDAO = userBundleDAO;
  }

  // ──────────────────────────────────────────────────────────────────────
  //  Runtime-config read path (consumed by OntologySeedService)
  // ──────────────────────────────────────────────────────────────────────

  /**
   * Find the singleton row, or first-seed it from the deploy-time
   * defaults. Idempotent — subsequent calls return the existing row.
   *
   * <p>The first-start seeding is: {@code preseedEnabled} ← deploy-time
   * {@code shepard.semantic.internal.preseed-ontologies.enabled};
   * {@code disabledBundles} ← deploy-time
   * {@code shepard.semantic.internal.preseed-ontologies.skip-bundles}.
   * Per CLAUDE.md "Always: surface operator knobs", the runtime row
   * wins forever after — deploy-time only seeds the install default.
   */
  public SemanticConfig loadSingleton() {
    SemanticConfig cfg = configDAO.findFirst();
    if (cfg != null) return cfg;

    SemanticConfig fresh = new SemanticConfig();
    fresh.setPreseedEnabled(readBooleanConfig(OntologySeedService.ENABLED_PROPERTY, true));
    Set<String> deployDisabled = OntologySeedService.parseSkipBundles(
      readStringConfig(OntologySeedService.SKIP_BUNDLES_PROPERTY, "")
    );
    fresh.setDisabledBundles(new ArrayList<>(deployDisabled));
    long now = System.currentTimeMillis();
    fresh.setCreatedAt(now);
    fresh.setUpdatedAt(now);
    fresh.setUpdatedBy(null);
    SemanticConfig saved = configDAO.createOrUpdate(fresh);
    Log.infof(
      "OntologyConfigService: seeded :SemanticConfig from deploy-time defaults (preseedEnabled=%b, disabledBundles=%s).",
      saved.isPreseedEnabled(),
      saved.getDisabledBundles()
    );
    return saved;
  }

  /**
   * Project the {@link SemanticConfig} row into the immutable
   * {@link RuntimeConfig} value the seed service consumes. Idempotent;
   * the seed-on-demand happens through {@link #loadSingleton()}.
   */
  public RuntimeConfig loadRuntimeConfig() {
    SemanticConfig cfg = loadSingleton();
    return RuntimeConfig.of(cfg.isPreseedEnabled(), cfg.getDisabledBundles());
  }

  /**
   * List every operator-uploaded bundle as an {@link OntologyEntry}
   * the seed loop can consume directly (same shape as the built-in
   * manifest entries).
   */
  public List<OntologyEntry> listUserEntries() {
    List<UserOntologyBundle> rows = userBundleDAO.listAll();
    List<OntologyEntry> out = new ArrayList<>(rows.size());
    for (UserOntologyBundle b : rows) {
      if (b == null) continue;
      Path file = bundleFilePath(b.getBundleId());
      out.add(
        new OntologyEntry(
          b.getBundleId(),
          b.getName() != null ? b.getName() : b.getBundleId(),
          file.toAbsolutePath().toString(),
          b.getIriPrefix(),
          b.getFormat() == null ? "Turtle" : b.getFormat(),
          b.getCanonicalUrl(),
          b.getLicense(),
          null,
          b.getSha256() == null ? "" : b.getSha256(),
          b.getByteSize() == null ? 0L : b.getByteSize(),
          false, // user uploads are never required
          OntologyEntry.Source.USER
        )
      );
    }
    return out;
  }

  // ──────────────────────────────────────────────────────────────────────
  //  Enable / disable
  // ──────────────────────────────────────────────────────────────────────

  /** Outcome of {@link #setBundleEnabled(String, boolean, String, List)}. */
  public enum SetEnabledResult {
    /** Flip applied (or was already in the requested state). */
    OK,
    /** Bundle id is unknown across built-ins + user uploads. */
    NOT_FOUND,
    /** Disable requested on a bundle whose manifest entry is required. */
    REQUIRED_CANNOT_DISABLE,
  }

  /**
   * Flip a bundle's runtime enabled state. The built-in manifest is
   * passed in so the service can check {@code required: true} without
   * round-tripping through the seed service; tests inject a synthetic
   * list directly.
   *
   * @param bundleId the bundle to flip
   * @param enabled  {@code true} to remove from {@code disabledBundles};
   *                 {@code false} to add
   * @param actor    audit user (nullable)
   * @param builtin  the in-memory built-in manifest (so we know if the
   *                 id is required)
   * @return the {@link SetEnabledResult}
   */
  public SetEnabledResult setBundleEnabled(String bundleId, boolean enabled, String actor, List<OntologyEntry> builtin) {
    if (bundleId == null || bundleId.isBlank()) return SetEnabledResult.NOT_FOUND;

    OntologyEntry builtinEntry = findById(builtin, bundleId);
    UserOntologyBundle userEntry = userBundleDAO.findByBundleId(bundleId);
    if (builtinEntry == null && userEntry == null) return SetEnabledResult.NOT_FOUND;

    if (!enabled && builtinEntry != null && builtinEntry.required) {
      return SetEnabledResult.REQUIRED_CANNOT_DISABLE;
    }

    SemanticConfig cfg = loadSingleton();
    List<String> current = new ArrayList<>(cfg.getDisabledBundles() == null ? List.of() : cfg.getDisabledBundles());
    boolean mutated = false;
    if (enabled) {
      mutated = current.removeIf(bundleId::equals);
    } else {
      if (!current.contains(bundleId)) {
        current.add(bundleId);
        mutated = true;
      }
    }
    if (mutated) {
      cfg.setDisabledBundles(current);
      cfg.setUpdatedAt(System.currentTimeMillis());
      cfg.setUpdatedBy(actor);
      configDAO.createOrUpdate(cfg);
      Log.infof(
        "OntologyConfigService: bundle '%s' %s by %s (disabledBundles=%s).",
        bundleId,
        enabled ? "enabled" : "disabled",
        actor == null ? "<unknown>" : actor,
        current
      );
    }
    return SetEnabledResult.OK;
  }

  // ──────────────────────────────────────────────────────────────────────
  //  Upload
  // ──────────────────────────────────────────────────────────────────────

  /** Outcome of an {@link #uploadBundle} call. Carries either the saved
   * row or a typed failure. */
  public static final class UploadResult {

    public enum Status {
      CREATED,
      DUPLICATE_ID,
      INVALID_TTL,
      TOO_LARGE,
      BAD_METADATA,
      IO_ERROR,
    }

    public final Status status;
    public final String reason;
    public final UserOntologyBundle saved;

    private UploadResult(Status status, String reason, UserOntologyBundle saved) {
      this.status = status;
      this.reason = reason;
      this.saved = saved;
    }

    public static UploadResult created(UserOntologyBundle saved) {
      return new UploadResult(Status.CREATED, null, saved);
    }

    public static UploadResult failure(Status status, String reason) {
      return new UploadResult(status, reason, null);
    }
  }

  /** Metadata supplied alongside an upload — mirrors the JSON the REST
   * layer parses out of the multipart's metadata part. */
  public static final class UploadMetadata {

    public final String id;
    public final String name;
    public final String iriPrefix;
    public final String canonicalUrl;
    public final String license;

    public UploadMetadata(String id, String name, String iriPrefix, String canonicalUrl, String license) {
      this.id = id;
      this.name = name;
      this.iriPrefix = iriPrefix;
      this.canonicalUrl = canonicalUrl;
      this.license = license;
    }
  }

  /**
   * Upload a new operator-supplied ontology bundle.
   *
   * <p>Validates:
   * <ul>
   *   <li>{@code id} present, matches {@link #BUNDLE_ID_PATTERN}, does
   *       not collide with a built-in or existing user id.</li>
   *   <li>{@code iriPrefix} + {@code license} present.</li>
   *   <li>Payload size ≤ {@link #MAX_UPLOAD_BYTES}.</li>
   *   <li>Payload parseable as Turtle (lightweight in-process
   *       heuristic — n10s does the authoritative parse at import
   *       time).</li>
   * </ul>
   *
   * <p>On success: SHA-256 the bytes, write them to
   * {@code <user-bundles-dir>/<id>.ttl}, persist a
   * {@link UserOntologyBundle} catalogue row. The bundle joins the
   * seed loop on the next startup (callers that want immediate
   * import call the existing N1c refresh endpoint after upload).
   */
  public UploadResult uploadBundle(byte[] payload, UploadMetadata meta, String actor, List<OntologyEntry> builtin) {
    // Metadata sanity
    if (meta == null || meta.id == null || !BUNDLE_ID_PATTERN.matcher(meta.id).matches()) {
      return UploadResult.failure(
        UploadResult.Status.BAD_METADATA,
        "id must match " + BUNDLE_ID_PATTERN.pattern()
      );
    }
    if (meta.iriPrefix == null || meta.iriPrefix.isBlank()) {
      return UploadResult.failure(UploadResult.Status.BAD_METADATA, "iriPrefix is required");
    }
    if (meta.license == null || meta.license.isBlank()) {
      return UploadResult.failure(UploadResult.Status.BAD_METADATA, "license is required");
    }

    // Payload sanity
    if (payload == null || payload.length == 0) {
      return UploadResult.failure(UploadResult.Status.INVALID_TTL, "payload is empty");
    }
    if (payload.length > MAX_UPLOAD_BYTES) {
      return UploadResult.failure(
        UploadResult.Status.TOO_LARGE,
        "payload " + payload.length + " bytes exceeds cap " + MAX_UPLOAD_BYTES
      );
    }

    String body;
    try {
      body = new String(payload, StandardCharsets.UTF_8);
    } catch (RuntimeException ex) {
      return UploadResult.failure(UploadResult.Status.INVALID_TTL, "payload is not valid UTF-8");
    }
    if (!looksLikeTurtle(body)) {
      return UploadResult.failure(
        UploadResult.Status.INVALID_TTL,
        "payload does not look like Turtle (no @prefix / IRI / triple shape detected)"
      );
    }

    // Id collision
    if (findById(builtin, meta.id) != null) {
      return UploadResult.failure(
        UploadResult.Status.DUPLICATE_ID,
        "bundle id '" + meta.id + "' shadows a built-in"
      );
    }
    if (userBundleDAO.findByBundleId(meta.id) != null) {
      return UploadResult.failure(
        UploadResult.Status.DUPLICATE_ID,
        "bundle id '" + meta.id + "' already exists in the user catalogue"
      );
    }

    // SHA-256 + write
    String sha;
    try {
      sha = sha256Hex(payload);
    } catch (RuntimeException ex) {
      return UploadResult.failure(UploadResult.Status.IO_ERROR, "could not hash payload: " + ex.getMessage());
    }

    Path target = bundleFilePath(meta.id);
    try {
      ensureUserBundlesDir();
      Path tmp = target.getParent().resolve(meta.id + ".ttl.tmp");
      Files.write(tmp, payload);
      Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException ex) {
      return UploadResult.failure(
        UploadResult.Status.IO_ERROR,
        "could not write bundle to disk: " + ex.getMessage()
      );
    }

    UserOntologyBundle row = new UserOntologyBundle();
    row.setBundleId(meta.id);
    row.setName(meta.name == null || meta.name.isBlank() ? meta.id : meta.name);
    row.setIriPrefix(meta.iriPrefix);
    row.setCanonicalUrl(meta.canonicalUrl);
    row.setLicense(meta.license);
    row.setSha256(sha);
    row.setByteSize((long) payload.length);
    row.setFormat("Turtle");
    row.setAddedBy(actor);
    row.setAddedAt(System.currentTimeMillis());
    UserOntologyBundle saved = userBundleDAO.createOrUpdate(row);
    Log.infof(
      "OntologyConfigService: user bundle '%s' uploaded by %s (sha256=%s, %d bytes).",
      meta.id,
      actor == null ? "<unknown>" : actor,
      sha,
      payload.length
    );
    return UploadResult.created(saved);
  }

  // ──────────────────────────────────────────────────────────────────────
  //  Remove
  // ──────────────────────────────────────────────────────────────────────

  public enum RemoveResult {
    REMOVED,
    NOT_FOUND,
    BUILTIN_NOT_REMOVABLE,
  }

  /**
   * Remove an operator-uploaded bundle (catalogue row + on-disk
   * file). Built-in ids are refused with
   * {@link RemoveResult#BUILTIN_NOT_REMOVABLE}.
   */
  public RemoveResult removeBundle(String bundleId, String actor, List<OntologyEntry> builtin) {
    if (bundleId == null || bundleId.isBlank()) return RemoveResult.NOT_FOUND;
    if (findById(builtin, bundleId) != null) {
      return RemoveResult.BUILTIN_NOT_REMOVABLE;
    }
    UserOntologyBundle row = userBundleDAO.findByBundleId(bundleId);
    if (row == null) return RemoveResult.NOT_FOUND;

    Path target = bundleFilePath(bundleId);
    try {
      Files.deleteIfExists(target);
    } catch (IOException ex) {
      // Service-level: we still drop the row even if disk delete
      // fails; the file is orphaned but a re-upload of the same id
      // will overwrite it via the atomic move path.
      Log.warnf(
        "OntologyConfigService: could not delete user-bundle file %s (%s); dropping catalogue row anyway.",
        target,
        ex.getMessage()
      );
    }

    if (row.getId() != null) {
      userBundleDAO.deleteByNeo4jId(row.getId());
    }
    Log.infof(
      "OntologyConfigService: user bundle '%s' removed by %s.",
      bundleId,
      actor == null ? "<unknown>" : actor
    );
    return RemoveResult.REMOVED;
  }

  // ──────────────────────────────────────────────────────────────────────
  //  Merged listing (for GET /v2/admin/semantic/ontologies)
  // ──────────────────────────────────────────────────────────────────────

  /**
   * Snapshot view of a single bundle for the admin REST list endpoint.
   */
  public static final class BundleView {

    public final String id;
    public final String name;
    public final String source; // "builtin" | "user"
    public final boolean required;
    public final boolean enabled;
    public final String iriPrefix;
    public final String canonicalUrl;
    public final String license;
    public final String sha256;
    public final long byteSize;

    public BundleView(
      String id,
      String name,
      String source,
      boolean required,
      boolean enabled,
      String iriPrefix,
      String canonicalUrl,
      String license,
      String sha256,
      long byteSize
    ) {
      this.id = id;
      this.name = name;
      this.source = source;
      this.required = required;
      this.enabled = enabled;
      this.iriPrefix = iriPrefix;
      this.canonicalUrl = canonicalUrl;
      this.license = license;
      this.sha256 = sha256;
      this.byteSize = byteSize;
    }
  }

  /**
   * Produce the merged view consumed by
   * {@code GET /v2/admin/semantic/ontologies}. Built-ins first
   * (manifest declaration order), then user bundles (id ASC). Each
   * row's {@code enabled} reflects the precedence: required bundles
   * are always {@code true}; otherwise it's "not in disabled
   * set".
   */
  public List<BundleView> listMerged(List<OntologyEntry> builtin) {
    SemanticConfig cfg = loadSingleton();
    Set<String> disabled = new LinkedHashSet<>(
      cfg.getDisabledBundles() == null ? List.of() : cfg.getDisabledBundles()
    );
    Set<String> deployTimeSkip = OntologySeedService.parseSkipBundles(
      readStringConfig(OntologySeedService.SKIP_BUNDLES_PROPERTY, "")
    );
    // Union: an id in either disable set is disabled (unless required).
    Set<String> effectiveDisabled = new LinkedHashSet<>(disabled);
    effectiveDisabled.addAll(deployTimeSkip);

    List<BundleView> out = new ArrayList<>();
    for (OntologyEntry e : builtin) {
      boolean enabled = e.required || !effectiveDisabled.contains(e.id);
      out.add(
        new BundleView(
          e.id,
          e.name,
          "builtin",
          e.required,
          enabled,
          e.iriPrefix,
          e.canonicalUrl,
          e.license,
          e.sha256,
          e.sizeBytes
        )
      );
    }
    for (OntologyEntry u : listUserEntries()) {
      boolean enabled = !effectiveDisabled.contains(u.id);
      out.add(
        new BundleView(
          u.id,
          u.name,
          "user",
          false,
          enabled,
          u.iriPrefix,
          u.canonicalUrl,
          u.license,
          u.sha256,
          u.sizeBytes
        )
      );
    }
    return out;
  }

  /**
   * Find one bundle by id across built-in + user, projected as a
   * {@link BundleView}. Returns {@link Optional#empty()} when unknown.
   */
  public Optional<BundleView> findBundle(String bundleId, List<OntologyEntry> builtin) {
    if (bundleId == null || bundleId.isBlank()) return Optional.empty();
    for (BundleView v : listMerged(builtin)) {
      if (bundleId.equals(v.id)) return Optional.of(v);
    }
    return Optional.empty();
  }

  // ──────────────────────────────────────────────────────────────────────
  //  Helpers
  // ──────────────────────────────────────────────────────────────────────

  /** Resolve the on-disk path for a user bundle's TTL. */
  Path bundleFilePath(String bundleId) {
    String dir = readStringConfig(
      OntologySeedService.USER_BUNDLES_DIR_PROPERTY,
      OntologySeedService.DEFAULT_USER_BUNDLES_DIR
    );
    return Path.of(dir, bundleId + ".ttl");
  }

  /**
   * Ensure the user-bundles directory exists. Idempotent; called
   * before each upload.
   */
  void ensureUserBundlesDir() throws IOException {
    String dir = readStringConfig(
      OntologySeedService.USER_BUNDLES_DIR_PROPERTY,
      OntologySeedService.DEFAULT_USER_BUNDLES_DIR
    );
    Files.createDirectories(Path.of(dir));
  }

  static OntologyEntry findById(List<OntologyEntry> list, String id) {
    if (list == null || id == null) return null;
    for (OntologyEntry e : list) {
      if (e != null && id.equals(e.id)) return e;
    }
    return null;
  }

  /**
   * Lightweight Turtle parse-shape heuristic. We deliberately do
   * <em>not</em> pull in RDF4J for a full parse — n10s does the
   * authoritative parse at import time, and an over-aggressive
   * pre-check would refuse valid Turtle that uses uncommon shapes.
   *
   * <p>Heuristic accepts the bundle if at least one of:
   * <ul>
   *   <li>contains a {@code @prefix} directive (covering the
   *       vast majority of real-world bundles)</li>
   *   <li>contains a {@code @base} directive</li>
   *   <li>contains a {@code PREFIX} keyword (SPARQL-style Turtle 1.1)</li>
   *   <li>contains an absolute IRI in angle brackets followed by a
   *       statement terminator (the trailing-dot Turtle minimum)</li>
   * </ul>
   */
  static boolean looksLikeTurtle(String body) {
    if (body == null || body.isBlank()) return false;
    String t = body;
    if (t.contains("@prefix") || t.contains("@base")) return true;
    if (t.toUpperCase(Locale.ROOT).contains("PREFIX ")) return true;
    int lt = t.indexOf('<');
    int gt = lt < 0 ? -1 : t.indexOf('>', lt + 1);
    if (lt >= 0 && gt > lt && t.indexOf('.', gt + 1) >= 0) return true;
    return false;
  }

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
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  static String readStringConfig(String key, String fallback) {
    try {
      return ConfigProvider.getConfig().getOptionalValue(key, String.class).orElse(fallback);
    } catch (RuntimeException ex) {
      return fallback;
    }
  }

  static boolean readBooleanConfig(String key, boolean fallback) {
    try {
      return ConfigProvider.getConfig().getOptionalValue(key, Boolean.class).orElse(fallback);
    } catch (RuntimeException ex) {
      return fallback;
    }
  }
}
