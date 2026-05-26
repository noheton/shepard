package de.dlr.shepard.context.semantic.services;

import de.dlr.shepard.context.semantic.OntologySeedService;
import de.dlr.shepard.context.semantic.daos.OntologyGitSourceDAO;
import de.dlr.shepard.context.semantic.daos.UserOntologyBundleDAO;
import de.dlr.shepard.context.semantic.entities.OntologyGitSource;
import de.dlr.shepard.context.semantic.entities.UserOntologyBundle;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * TPL5 — service that ingests ontologies from Git repositories.
 *
 * <p>Three entry points:
 * <ol>
 *   <li><b>Nightly scheduler</b> — {@link #runNightly()} calls
 *       {@link #ingestAll()} for every enabled
 *       {@link OntologyGitSource} at 02:00 server-local time (Quartz
 *       cron {@code "0 0 2 * * ?"}). Feature-flagged via
 *       {@code shepard.tpl5.git-ingest.enabled} (default {@code false}).</li>
 *   <li><b>On-demand via REST</b> — the admin endpoint
 *       {@code POST /v2/admin/semantic/git-sources/{appId}/ingest}
 *       calls {@link #ingest(OntologyGitSource)} directly.</li>
 *   <li><b>Bulk on-demand</b> — {@link #ingestAll()} also accessible
 *       from the REST layer if needed.</li>
 * </ol>
 *
 * <p><b>Git clone strategy.</b> {@link ProcessBuilder} shells out to
 * the system {@code git} binary with {@code --depth=1
 * --branch <branch>}. No Java git library is added to the dependency
 * tree; the OS-level git handles authentication (SSH agent, HTTPS
 * credential helpers) transparently. The temp directory is deleted
 * after each ingest run regardless of success.
 *
 * <p><b>Bundle-id derivation.</b> For each matching file:
 * {@code git-<nameSlug>-<fileStem>} where {@code nameSlug} is the
 * first 16 chars of the source's {@code name}, lowercased and
 * sanitized; {@code fileStem} is the filename without extension,
 * similarly sanitized. On re-ingest the service deletes the old
 * bundle (if any) and re-uploads, so bytes stay current.
 *
 * <p><b>URL + branch security validation.</b>
 * {@link #validateRepoUrl(String)} and {@link #validateBranch(String)}
 * reject inputs that could cause argument injection even via
 * {@link ProcessBuilder} (e.g. a branch name like
 * {@code "main --upload-pack=evil"}).
 *
 * @see de.dlr.shepard.context.semantic.entities.OntologyGitSource
 * @see de.dlr.shepard.context.semantic.daos.OntologyGitSourceDAO
 */
@ApplicationScoped
public class OntologyGitIngestService {

  /** Max time allowed for a git clone, in seconds. */
  static final int GIT_TIMEOUT_SECONDS = 120;

  /** Max file size accepted from a cloned repo (bytes). Same cap as upload. */
  static final long MAX_FILE_BYTES = OntologyConfigService.MAX_UPLOAD_BYTES;

  /**
   * Accepted repo-URL prefixes — only https:// and git@host: to
   * prevent file:// or ftp:// access.
   */
  static final Pattern ALLOWED_URL_PREFIX = Pattern.compile(
    "^(https://|git@[a-zA-Z0-9._-]+:)",
    Pattern.CASE_INSENSITIVE
  );

  /**
   * Branch name allowlist — alphanumeric plus a handful of
   * separators used in real branch naming conventions. Rejects
   * anything that could be parsed as a git option ({@code --}).
   */
  static final Pattern SAFE_BRANCH = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/\\-]{0,99}$");

  @Inject
  OntologyGitSourceDAO gitSourceDAO;

  @Inject
  UserOntologyBundleDAO userBundleDAO;

  @Inject
  OntologyConfigService ontologyConfigService;

  @ConfigProperty(name = "shepard.tpl5.git-ingest.enabled", defaultValue = "false")
  boolean schedulerEnabled;

  // ────────────────────────────────────────────────────────────────────────
  //  Scheduler
  // ────────────────────────────────────────────────────────────────────────

  /**
   * Nightly at 02:00 (Quartz 6-field cron). Guarded by
   * {@code shepard.tpl5.git-ingest.enabled} (default {@code false})
   * so a fresh install doesn't try to clone anything until an admin
   * has configured git sources.
   */
  @Scheduled(cron = "0 0 2 * * ?", identity = "ontology-git-ingest")
  @ActivateRequestContext
  public void runNightly() {
    if (!schedulerEnabled) {
      Log.debug("TPL5 git-ingest skipped — shepard.tpl5.git-ingest.enabled=false");
      return;
    }
    Log.info("TPL5 nightly ontology-git-ingest starting.");
    IngestSummary summary = ingestAll();
    Log.infof(
      "TPL5 nightly ontology-git-ingest done: %d source(s), %d file(s) ingested, %d error(s).",
      summary.sourcesAttempted,
      summary.filesIngested,
      summary.errors
    );
  }

  // ────────────────────────────────────────────────────────────────────────
  //  Public API
  // ────────────────────────────────────────────────────────────────────────

  /**
   * Run ingest for every enabled {@link OntologyGitSource}.
   *
   * @return a summary of the run (sources attempted, files ingested,
   *         error count).
   */
  public IngestSummary ingestAll() {
    List<OntologyGitSource> sources = gitSourceDAO.listEnabled();
    int sourcesAttempted = 0;
    int filesIngested = 0;
    int errors = 0;
    for (OntologyGitSource source : sources) {
      sourcesAttempted++;
      IngestResult result = ingest(source);
      filesIngested += result.filesIngested;
      if (!result.ok) errors++;
    }
    return new IngestSummary(sourcesAttempted, filesIngested, errors);
  }

  /**
   * Run ingest for a single {@link OntologyGitSource} — used by the
   * manual-trigger REST endpoint and {@link #ingestAll()}.
   *
   * <p>The method is deliberately not {@code synchronized}: concurrent
   * runs of the same source would produce duplicate bundles which the
   * delete-and-re-upload path would tolerate. In practice the admin
   * UI is single-user and the scheduler has a single identity so
   * concurrent runs on the same source are unlikely.
   *
   * @param source the source to ingest (must be non-null)
   * @return the result of this run
   */
  public IngestResult ingest(OntologyGitSource source) {
    if (source == null) return IngestResult.error("source is null", 0);

    Log.infof("TPL5: starting ingest for git source '%s' (%s).", source.getName(), source.getAppId());

    // Mark pending
    source.setLastStatus("PENDING");
    source = gitSourceDAO.createOrUpdate(source);

    // Validate URL + branch before touching the filesystem
    String validationError = validateInputs(source);
    if (validationError != null) {
      return finishWithError(source, validationError, 0);
    }

    // Clone into a fresh temp directory
    Path tmpDir;
    try {
      tmpDir = Files.createTempDirectory("shepard-git-ingest-");
    } catch (IOException ex) {
      return finishWithError(source, "Could not create temp directory: " + ex.getMessage(), 0);
    }

    try {
      // Clone
      String cloneError = cloneRepo(source.getRepoUrl(), source.getBranch(), tmpDir);
      if (cloneError != null) {
        return finishWithError(source, cloneError, 0);
      }

      // Find matching files
      List<Path> files;
      try {
        files = findMatchingFiles(tmpDir, source.getPathPattern());
      } catch (IOException ex) {
        return finishWithError(source, "Could not walk cloned repo: " + ex.getMessage(), 0);
      }
      Log.infof("TPL5: git source '%s' — %d file(s) matched pattern '%s'.",
        source.getName(), files.size(), source.getPathPattern());

      // Ingest each file
      int ingested = 0;
      List<String> fileErrors = new ArrayList<>();
      String nameSlug = deriveNameSlug(source.getName(), source.getAppId());

      // Load builtin manifest once for collision checks
      List<OntologySeedService.OntologyEntry> builtinManifest;
      try {
        builtinManifest = new OntologySeedService().loadManifest();
      } catch (RuntimeException ex) {
        Log.warnf("TPL5: could not load built-in manifest — proceeding with empty list (%s).", ex.getMessage());
        builtinManifest = List.of();
      }

      for (Path file : files) {
        String err = ingestFile(file, nameSlug, source, builtinManifest);
        if (err == null) {
          ingested++;
        } else {
          fileErrors.add(file.getFileName() + ": " + err);
        }
      }

      if (!fileErrors.isEmpty()) {
        String combinedError = String.join("; ", fileErrors);
        return finishWithError(source, combinedError, ingested);
      }

      return finishWithOk(source, ingested);

    } finally {
      deleteTempDir(tmpDir);
    }
  }

  // ────────────────────────────────────────────────────────────────────────
  //  Validation
  // ────────────────────────────────────────────────────────────────────────

  /**
   * Validate both URL and branch in one call. Returns an error message
   * or {@code null} when inputs are good.
   */
  String validateInputs(OntologyGitSource source) {
    String urlErr = validateRepoUrl(source.getRepoUrl());
    if (urlErr != null) return urlErr;
    String branchErr = validateBranch(source.getBranch());
    if (branchErr != null) return branchErr;
    if (source.getPathPattern() == null || source.getPathPattern().isBlank()) {
      return "pathPattern must not be blank";
    }
    return null;
  }

  /**
   * Validate the repo URL. Returns an error string or {@code null}.
   *
   * <p>Accepts only {@code https://} and {@code git@host:} prefixes
   * to prevent file:// or ftp:// abuse. Also rejects URLs containing
   * space, backslash, shell metacharacters, or option-injection
   * ({@code --}) strings.
   */
  public static String validateRepoUrl(String repoUrl) {
    if (repoUrl == null || repoUrl.isBlank()) {
      return "repoUrl must not be blank";
    }
    if (!ALLOWED_URL_PREFIX.matcher(repoUrl).find()) {
      return "repoUrl must start with https:// or git@host: — got: " + repoUrl;
    }
    // Reject shell metacharacters and option-injection
    if (repoUrl.contains(" ") || repoUrl.contains("\t") || repoUrl.contains("\n")
      || repoUrl.contains("\\") || repoUrl.contains(";") || repoUrl.contains("&")
      || repoUrl.contains("|") || repoUrl.contains(">") || repoUrl.contains("<")
      || repoUrl.contains("`") || repoUrl.contains("$") || repoUrl.contains("'")
      || repoUrl.contains("\"")) {
      return "repoUrl contains forbidden shell metacharacters";
    }
    return null;
  }

  /**
   * Validate the branch name. Returns an error string or {@code null}.
   *
   * <p>Accepts only {@code ^[A-Za-z0-9][A-Za-z0-9._/\\-]{0,99}$} —
   * matches real-world branch naming conventions and explicitly rejects
   * {@code --} which would be parsed as a git option flag.
   */
  public static String validateBranch(String branch) {
    if (branch == null || branch.isBlank()) {
      return "branch must not be blank";
    }
    if (!SAFE_BRANCH.matcher(branch).matches()) {
      return "branch contains forbidden characters: " + branch;
    }
    return null;
  }

  // ────────────────────────────────────────────────────────────────────────
  //  Git clone
  // ────────────────────────────────────────────────────────────────────────

  /**
   * Clone the repo into {@code targetDir}. Returns {@code null} on
   * success, or an error message on failure.
   *
   * <p>Delegates to {@link #runGit(List, Path)} so tests can override
   * the actual process execution.
   */
  String cloneRepo(String repoUrl, String branch, Path targetDir) {
    List<String> args = List.of(
      "git", "clone",
      "--depth=1",
      "--branch", branch,
      repoUrl,
      targetDir.toAbsolutePath().toString()
    );
    int exit = runGit(args, null);
    if (exit != 0) {
      return "git clone exited with status " + exit +
        " (url=" + repoUrl + ", branch=" + branch + ")";
    }
    return null;
  }

  /**
   * Execute a git command via {@link ProcessBuilder}. Returns the
   * process exit code.
   *
   * <p><b>Test seam</b> — subclasses (or tests) override this method
   * to intercept the git invocation without a real network connection.
   * The {@code args} list is the complete command; {@code workDir} is
   * the working directory (may be {@code null}).
   *
   * @param args    full command including {@code "git"} as first element
   * @param workDir working directory for the process, or {@code null}
   * @return the process exit code (0 = success)
   */
  protected int runGit(List<String> args, Path workDir) {
    try {
      ProcessBuilder pb = new ProcessBuilder(args);
      pb.redirectErrorStream(true);
      if (workDir != null) pb.directory(workDir.toFile());
      Process proc = pb.start();
      // Drain stdout/stderr to prevent buffer stall
      try (InputStream is = proc.getInputStream()) {
        is.transferTo(java.io.OutputStream.nullOutputStream());
      }
      boolean finished = proc.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!finished) {
        proc.destroyForcibly();
        Log.warnf("TPL5: git command timed out after %ds: %s", GIT_TIMEOUT_SECONDS, args);
        return -1;
      }
      return proc.exitValue();
    } catch (IOException | InterruptedException ex) {
      Log.warnf("TPL5: git command failed (%s): %s", ex.getClass().getSimpleName(), args);
      if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
      return -1;
    }
  }

  // ────────────────────────────────────────────────────────────────────────
  //  File discovery + ingest
  // ────────────────────────────────────────────────────────────────────────

  /**
   * Walk {@code cloneRoot} and collect all files whose path matches
   * {@code pathPattern} (glob, evaluated relative to the clone root).
   *
   * <p>Uses {@link java.nio.file.FileSystem#getPathMatcher} with the
   * {@code "glob:"} prefix.  A pattern without a directory separator
   * (e.g. {@code "*.ttl"}) matches files at any depth using the
   * {@code **} prefix added automatically.
   */
  List<Path> findMatchingFiles(Path cloneRoot, String pathPattern) throws IOException {
    // For a bare pattern like "*.ttl" (no directory separator) we match both
    // "*.ttl" (root level) and "**/*.ttl" (any subdirectory depth).
    // For a path-qualified pattern like "ontologies/*.ttl" we match as-is.
    final boolean barePattern = !pathPattern.contains("/");
    PathMatcher deepMatcher = cloneRoot.getFileSystem().getPathMatcher(
      "glob:" + (barePattern ? "**/" + pathPattern : pathPattern)
    );
    PathMatcher rootMatcher = barePattern
      ? cloneRoot.getFileSystem().getPathMatcher("glob:" + pathPattern)
      : null;

    List<Path> result = new ArrayList<>();
    Files.walkFileTree(cloneRoot, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        Path rel = cloneRoot.relativize(file);
        boolean matched = deepMatcher.matches(rel)
          || (rootMatcher != null && rootMatcher.matches(rel));
        if (matched) result.add(file);
        return FileVisitResult.CONTINUE;
      }
    });
    return result;
  }

  /**
   * Ingest a single file into the {@link OntologyConfigService} user
   * bundle catalogue.
   *
   * <p>Bundle id: {@code git-<nameSlug>-<fileStem>}. On re-ingest the
   * old bundle is deleted first (delete-then-upload ensures bytes stay
   * current without triggering the DUPLICATE_ID guard).
   *
   * @return {@code null} on success, or an error message
   */
  String ingestFile(
    Path file,
    String nameSlug,
    OntologyGitSource source,
    List<OntologySeedService.OntologyEntry> builtinManifest
  ) {
    // Size check
    long fileSize;
    try {
      fileSize = Files.size(file);
    } catch (IOException ex) {
      return "could not stat file: " + ex.getMessage();
    }
    if (fileSize > MAX_FILE_BYTES) {
      return "file too large: " + fileSize + " bytes (max " + MAX_FILE_BYTES + ")";
    }

    // Read bytes
    byte[] bytes;
    try {
      bytes = Files.readAllBytes(file);
    } catch (IOException ex) {
      return "could not read file: " + ex.getMessage();
    }

    // Derive bundle id
    String fileStem = fileStem(file.getFileName().toString());
    String bundleId = "git-" + nameSlug + "-" + fileStem;
    // Truncate to 64 chars (OntologyConfigService.BUNDLE_ID_PATTERN max)
    if (bundleId.length() > 64) bundleId = bundleId.substring(0, 64);

    // Delete existing bundle with this id if present (idempotent re-ingest)
    String actor = "git-ingest:" + source.getAppId();
    UserOntologyBundle existing = userBundleDAO.findByBundleId(bundleId);
    if (existing != null) {
      ontologyConfigService.removeBundle(bundleId, actor, builtinManifest);
    }

    // Build metadata
    OntologyConfigService.UploadMetadata meta = new OntologyConfigService.UploadMetadata(
      bundleId,
      source.getName() + " / " + file.getFileName(),
      "", // iriPrefix — not reliably detectable from filename; placeholder
      null,
      "unknown" // license is declared on source; placeholder until per-file meta lands
    );

    OntologyConfigService.UploadResult result = ontologyConfigService.uploadBundle(
      bytes,
      meta,
      actor,
      builtinManifest
    );

    if (result.status != OntologyConfigService.UploadResult.Status.CREATED) {
      return "upload failed (" + result.status + "): " + result.reason;
    }

    Log.infof("TPL5: ingested '%s' from git source '%s' (bundleId=%s).",
      file.getFileName(), source.getName(), bundleId);
    return null;
  }

  // ────────────────────────────────────────────────────────────────────────
  //  Status tracking helpers
  // ────────────────────────────────────────────────────────────────────────

  private IngestResult finishWithOk(OntologyGitSource source, int filesIngested) {
    source.setLastStatus("OK");
    source.setLastError(null);
    source.setLastIngestedAt(System.currentTimeMillis());
    gitSourceDAO.createOrUpdate(source);
    Log.infof("TPL5: ingest OK for git source '%s' — %d file(s).", source.getName(), filesIngested);
    return IngestResult.ok(filesIngested);
  }

  private IngestResult finishWithError(OntologyGitSource source, String error, int filesIngested) {
    String truncated = error != null && error.length() > 4096 ? error.substring(0, 4096) : error;
    source.setLastStatus("ERROR");
    source.setLastError(truncated);
    source.setLastIngestedAt(System.currentTimeMillis());
    gitSourceDAO.createOrUpdate(source);
    Log.warnf("TPL5: ingest ERROR for git source '%s': %s (filesIngested=%d).",
      source.getName(), truncated, filesIngested);
    return IngestResult.error(truncated, filesIngested);
  }

  // ────────────────────────────────────────────────────────────────────────
  //  Utility
  // ────────────────────────────────────────────────────────────────────────

  /**
   * Derive the sanitized name slug used in bundle id derivation.
   * Uses the first 16 characters of the source name, lowercased
   * and with non-{@code [a-z0-9_-]} chars replaced by hyphens.
   * Falls back to a prefix of the appId when name is blank.
   */
  static String deriveNameSlug(String name, String appId) {
    String base = (name != null && !name.isBlank()) ? name : (appId != null ? appId : "unknown");
    String lower = base.toLowerCase(Locale.ROOT);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < lower.length() && sb.length() < 16; i++) {
      char c = lower.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
        sb.append(c);
      } else {
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '-') sb.append('-');
      }
    }
    // Trim trailing hyphens
    String slug = sb.toString().replaceAll("-+$", "");
    return slug.isEmpty() ? "src" : slug;
  }

  /**
   * Extract the filename stem (name without last extension).
   * Sanitized to {@code [a-z0-9_-]}, truncated to 40 chars.
   */
  static String fileStem(String filename) {
    if (filename == null || filename.isBlank()) return "ontology";
    int dot = filename.lastIndexOf('.');
    String stem = (dot > 0) ? filename.substring(0, dot) : filename;
    String lower = stem.toLowerCase(Locale.ROOT);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < lower.length() && sb.length() < 40; i++) {
      char c = lower.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
        sb.append(c);
      } else {
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '-') sb.append('-');
      }
    }
    String slug = sb.toString().replaceAll("-+$", "");
    return slug.isEmpty() ? "ontology" : slug;
  }

  /**
   * Recursively delete a directory tree, swallowing errors.
   * Always called in {@code finally} so we don't leave temp dirs.
   */
  static void deleteTempDir(Path dir) {
    if (dir == null) return;
    try {
      Files.walkFileTree(dir, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.deleteIfExists(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
          Files.deleteIfExists(d);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException ex) {
      Log.warnf("TPL5: could not fully delete temp dir %s: %s", dir, ex.getMessage());
    }
  }

  // ────────────────────────────────────────────────────────────────────────
  //  Result / Summary value types
  // ────────────────────────────────────────────────────────────────────────

  /** The outcome of a single-source ingest run. */
  public static final class IngestResult {

    public final boolean ok;
    public final int filesIngested;
    public final String error;

    private IngestResult(boolean ok, int filesIngested, String error) {
      this.ok = ok;
      this.filesIngested = filesIngested;
      this.error = error;
    }

    public static IngestResult ok(int filesIngested) {
      return new IngestResult(true, filesIngested, null);
    }

    public static IngestResult error(String error, int filesIngested) {
      return new IngestResult(false, filesIngested, error);
    }
  }

  /** Summary across an {@link #ingestAll()} run. */
  public static final class IngestSummary {

    public final int sourcesAttempted;
    public final int filesIngested;
    public final int errors;

    public IngestSummary(int sourcesAttempted, int filesIngested, int errors) {
      this.sourcesAttempted = sourcesAttempted;
      this.filesIngested = filesIngested;
      this.errors = errors;
    }
  }
}
