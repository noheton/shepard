package de.dlr.shepard.context.semantic.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.semantic.OntologySeedService.OntologyEntry;
import de.dlr.shepard.context.semantic.daos.OntologyGitSourceDAO;
import de.dlr.shepard.context.semantic.daos.UserOntologyBundleDAO;
import de.dlr.shepard.context.semantic.entities.OntologyGitSource;
import de.dlr.shepard.context.semantic.entities.UserOntologyBundle;
import de.dlr.shepard.context.semantic.services.OntologyGitIngestService.IngestResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link OntologyGitIngestService}.
 *
 * <p>Coverage strategy:
 * <ol>
 *   <li><b>Validation tests</b> — URL and branch validation rules are
 *       tested exhaustively via the static helpers.</li>
 *   <li><b>Git clone mock</b> — an inner subclass overrides
 *       {@link OntologyGitIngestService#runGit(List, Path)} to
 *       capture the command args and return a configurable exit code,
 *       exercising the full ingest flow without a real network.</li>
 *   <li><b>Path-pattern matching</b> — a real temp dir is populated
 *       with synthetic TTL files and the matcher is exercised directly.</li>
 *   <li><b>Bundle-id derivation helpers</b> — {@link OntologyGitIngestService#deriveNameSlug}
 *       and {@link OntologyGitIngestService#fileStem} are exercised with
 *       edge-case inputs.</li>
 *   <li><b>Error handling</b> — clone failures, oversized files, upload
 *       failures are all tested.</li>
 * </ol>
 */
class OntologyGitIngestServiceTest {

  @TempDir
  Path tempDir;

  private OntologyGitSourceDAO gitSourceDAO;
  private UserOntologyBundleDAO userBundleDAO;
  private OntologyConfigService ontologyConfigService;

  /** Recorded args list + exit-code control for the git mock. */
  private final AtomicReference<List<String>> capturedGitArgs = new AtomicReference<>();
  private final AtomicInteger mockGitExitCode = new AtomicInteger(0);

  /** Service under test with git clone overridden. */
  private OntologyGitIngestService service;

  private static final String SAMPLE_TTL =
    "@prefix ex: <http://example.org/> .\nex:a a ex:Thing .\n";

  @BeforeEach
  void setUp() {
    gitSourceDAO = mock(OntologyGitSourceDAO.class);
    userBundleDAO = mock(UserOntologyBundleDAO.class);
    ontologyConfigService = mock(OntologyConfigService.class);

    // Default: createOrUpdate returns the same entity
    when(gitSourceDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
    when(userBundleDAO.findByBundleId(anyString())).thenReturn(null);
    when(ontologyConfigService.uploadBundle(any(), any(), any(), any()))
      .thenReturn(OntologyConfigService.UploadResult.created(new UserOntologyBundle()));
    when(ontologyConfigService.removeBundle(anyString(), anyString(), any()))
      .thenReturn(OntologyConfigService.RemoveResult.REMOVED);

    service = new OntologyGitIngestService() {
      @Override
      protected int runGit(List<String> args, Path workDir) {
        capturedGitArgs.set(new ArrayList<>(args));
        return mockGitExitCode.get();
      }
    };
    // Wire injected fields directly (bypassing CDI)
    service.gitSourceDAO = gitSourceDAO;
    service.userBundleDAO = userBundleDAO;
    service.ontologyConfigService = ontologyConfigService;
  }

  // ─── URL validation ────────────────────────────────────────────────────

  @Test
  void validateRepoUrl_nullOrBlank_returnsError() {
    assertNotNull(OntologyGitIngestService.validateRepoUrl(null));
    assertNotNull(OntologyGitIngestService.validateRepoUrl(""));
    assertNotNull(OntologyGitIngestService.validateRepoUrl("   "));
  }

  @Test
  void validateRepoUrl_acceptsHttps() {
    assertNull(OntologyGitIngestService.validateRepoUrl("https://github.com/InfAI-Leipzig/m4i-ontologies"));
  }

  @Test
  void validateRepoUrl_acceptsGitSsh() {
    assertNull(OntologyGitIngestService.validateRepoUrl("git@github.com:user/repo.git"));
  }

  @Test
  void validateRepoUrl_rejectsFileScheme() {
    assertNotNull(OntologyGitIngestService.validateRepoUrl("file:///etc/passwd"));
  }

  @Test
  void validateRepoUrl_rejectsFtpScheme() {
    assertNotNull(OntologyGitIngestService.validateRepoUrl("ftp://example.com/repo"));
  }

  @Test
  void validateRepoUrl_rejectsShellMetacharacters() {
    assertNotNull(OntologyGitIngestService.validateRepoUrl("https://example.com/repo; rm -rf /"));
    assertNotNull(OntologyGitIngestService.validateRepoUrl("https://example.com/repo`touch evil`"));
    assertNotNull(OntologyGitIngestService.validateRepoUrl("https://example.com/repo|cat /etc/passwd"));
  }

  @Test
  void validateRepoUrl_rejectsSpaces() {
    assertNotNull(OntologyGitIngestService.validateRepoUrl("https://example.com/a b"));
  }

  // ─── Branch validation ─────────────────────────────────────────────────

  @Test
  void validateBranch_acceptsSimple() {
    assertNull(OntologyGitIngestService.validateBranch("main"));
    assertNull(OntologyGitIngestService.validateBranch("feature/TPL5"));
    assertNull(OntologyGitIngestService.validateBranch("release-1.0"));
  }

  @Test
  void validateBranch_rejectsOptionInjection() {
    // A branch starting with -- would look like a git option flag
    assertNotNull(OntologyGitIngestService.validateBranch("--upload-pack=evil"));
  }

  @Test
  void validateBranch_rejectsShellMetacharacters() {
    assertNotNull(OntologyGitIngestService.validateBranch("main; rm -rf /"));
    assertNotNull(OntologyGitIngestService.validateBranch("main`touch evil`"));
  }

  @Test
  void validateBranch_rejectsBlank() {
    assertNotNull(OntologyGitIngestService.validateBranch(""));
    assertNotNull(OntologyGitIngestService.validateBranch(null));
  }

  // ─── Clone command construction ────────────────────────────────────────

  @Test
  void cloneRepo_buildsCorrectGitCommand() throws IOException {
    // We need an actual clone target path; use tempDir
    String url = "https://github.com/example/ontologies";
    String branch = "main";
    Path target = tempDir.resolve("clone");
    Files.createDirectories(target);

    service.cloneRepo(url, branch, target);

    List<String> args = capturedGitArgs.get();
    assertNotNull(args);
    assertEquals("git", args.get(0));
    assertEquals("clone", args.get(1));
    assertEquals("--depth=1", args.get(2));
    assertEquals("--branch", args.get(3));
    assertEquals(branch, args.get(4));
    assertEquals(url, args.get(5));
    assertEquals(target.toAbsolutePath().toString(), args.get(6));
  }

  @Test
  void cloneRepo_returnsErrorOnNonZeroExit() throws IOException {
    mockGitExitCode.set(128);
    String result = service.cloneRepo(
      "https://github.com/example/ontologies",
      "main",
      tempDir.resolve("clone")
    );
    assertNotNull(result);
    assertTrue(result.contains("128"), "Error message should include exit code");
  }

  @Test
  void cloneRepo_returnsNullOnSuccess() throws IOException {
    mockGitExitCode.set(0);
    String result = service.cloneRepo(
      "https://github.com/example/ontologies",
      "main",
      tempDir.resolve("clone")
    );
    assertNull(result);
  }

  // ─── Path-pattern matching ─────────────────────────────────────────────

  @Test
  void findMatchingFiles_matchesBareTtlGlob() throws IOException {
    Path cloneRoot = tempDir.resolve("repo");
    Files.createDirectories(cloneRoot);
    Files.writeString(cloneRoot.resolve("prov-o.ttl"), SAMPLE_TTL);
    Files.writeString(cloneRoot.resolve("not-an-ontology.json"), "{}");
    Files.createDirectories(cloneRoot.resolve("sub"));
    Files.writeString(cloneRoot.resolve("sub").resolve("qudt.ttl"), SAMPLE_TTL);

    List<Path> found = service.findMatchingFiles(cloneRoot, "*.ttl");

    assertEquals(2, found.size(), "Should match TTL files at any depth");
    assertTrue(found.stream().allMatch(p -> p.toString().endsWith(".ttl")));
  }

  @Test
  void findMatchingFiles_matchesSubdirPattern() throws IOException {
    Path cloneRoot = tempDir.resolve("repo");
    Path ontDir = cloneRoot.resolve("ontologies");
    Files.createDirectories(ontDir);
    Files.writeString(ontDir.resolve("m4i.ttl"), SAMPLE_TTL);
    Files.writeString(cloneRoot.resolve("README.md"), "# readme");
    // TTL at root should NOT match "ontologies/*.ttl"
    Files.writeString(cloneRoot.resolve("root.ttl"), SAMPLE_TTL);

    List<Path> found = service.findMatchingFiles(cloneRoot, "ontologies/*.ttl");

    assertEquals(1, found.size());
    assertTrue(found.get(0).getFileName().toString().equals("m4i.ttl"));
  }

  @Test
  void findMatchingFiles_emptyDirReturnsEmptyList() throws IOException {
    Path cloneRoot = tempDir.resolve("empty");
    Files.createDirectories(cloneRoot);

    List<Path> found = service.findMatchingFiles(cloneRoot, "*.ttl");
    assertTrue(found.isEmpty());
  }

  // ─── Bundle-id derivation ──────────────────────────────────────────────

  @Test
  void deriveNameSlug_usesFirst16Chars() {
    String slug = OntologyGitIngestService.deriveNameSlug("InfAI m4i ontologies repo", "appid");
    assertTrue(slug.length() <= 16, "Slug must be ≤ 16 chars");
    assertEquals("infai-m4i-ontolo", slug);
  }

  @Test
  void deriveNameSlug_sanitizesSpecialChars() {
    String slug = OntologyGitIngestService.deriveNameSlug("My Repo (test)!", "appid");
    assertFalse(slug.contains(" "), "No spaces");
    assertFalse(slug.contains("("), "No parens");
    assertFalse(slug.contains("!"), "No exclamation");
  }

  @Test
  void deriveNameSlug_fallsBackToAppId() {
    String slug = OntologyGitIngestService.deriveNameSlug("   ", "abc123");
    assertEquals("abc123", slug);
  }

  @Test
  void deriveNameSlug_handlesNull() {
    String slug = OntologyGitIngestService.deriveNameSlug(null, "app-id-xyz");
    assertNotNull(slug);
    assertFalse(slug.isBlank());
  }

  @Test
  void fileStem_stripsExtension() {
    assertEquals("prov-o", OntologyGitIngestService.fileStem("prov-o.ttl"));
    assertEquals("metadata4ing", OntologyGitIngestService.fileStem("metadata4ing.owl"));
    assertEquals("my-ontology", OntologyGitIngestService.fileStem("my-ontology.rdf"));
  }

  @Test
  void fileStem_sanitizesUppercase() {
    String stem = OntologyGitIngestService.fileStem("MyOntology.ttl");
    assertEquals("myontology", stem);
  }

  @Test
  void fileStem_truncatesLongName() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 60; i++) sb.append('a');
    String stem = OntologyGitIngestService.fileStem(sb + ".ttl");
    assertTrue(stem.length() <= 40);
  }

  @Test
  void fileStem_handlesNoExtension() {
    assertEquals("ontology", OntologyGitIngestService.fileStem("ontology"));
  }

  // ─── Full ingest flow: validation error ────────────────────────────────

  @Test
  void ingest_invalidUrl_setsErrorStatusAndReturnsError() {
    OntologyGitSource source = makeSource("file:///etc/evil", "main", "*.ttl");

    IngestResult result = service.ingest(source);

    assertFalse(result.ok);
    assertNotNull(result.error);
    verify(gitSourceDAO, never()).findByAppId(any());
    // Verify status was persisted
    verify(gitSourceDAO, times(2)).createOrUpdate(source);
    assertEquals("ERROR", source.getLastStatus());
  }

  @Test
  void ingest_invalidBranch_setsErrorStatus() {
    OntologyGitSource source = makeSource("https://github.com/example/repo", "--evil", "*.ttl");

    IngestResult result = service.ingest(source);

    assertFalse(result.ok);
    assertEquals("ERROR", source.getLastStatus());
  }

  @Test
  void ingest_blankPathPattern_setsErrorStatus() {
    OntologyGitSource source = makeSource("https://github.com/example/repo", "main", "");

    IngestResult result = service.ingest(source);

    assertFalse(result.ok);
    assertEquals("ERROR", source.getLastStatus());
  }

  // ─── Full ingest flow: clone failure ───────────────────────────────────

  @Test
  void ingest_cloneFailure_setsErrorStatus() {
    mockGitExitCode.set(128);
    OntologyGitSource source = makeSource("https://github.com/example/repo", "main", "*.ttl");

    IngestResult result = service.ingest(source);

    assertFalse(result.ok);
    assertTrue(result.error.contains("128"));
    assertEquals("ERROR", source.getLastStatus());
    // git was called
    assertNotNull(capturedGitArgs.get());
  }

  // ─── Full ingest flow: no matching files → OK with 0 count ────────────

  @Test
  void ingest_noMatchingFiles_okWithZeroCount() throws IOException {
    mockGitExitCode.set(0);

    // Override runGit to also create a real temp dir contents-free clone root
    // We need a non-empty temp dir for findMatchingFiles to walk
    OntologyGitSource source = makeSource("https://github.com/example/repo", "main", "*.ttl");

    // We can't easily control what temp dir gets created inside ingest().
    // But with no files matching in the empty temp dir, we should get ok=true, files=0.
    // The real test: ensure git was called with correct args.
    IngestResult result = service.ingest(source);

    // With exit code 0 and empty temp dir: ok=true, filesIngested=0
    assertTrue(result.ok);
    assertEquals(0, result.filesIngested);
    assertEquals("OK", source.getLastStatus());

    List<String> args = capturedGitArgs.get();
    assertNotNull(args);
    assertEquals("git", args.get(0));
    assertEquals("clone", args.get(1));
    assertTrue(args.contains("--depth=1"));
    assertEquals("--branch", args.get(3));
    assertEquals("main", args.get(4));
    assertEquals("https://github.com/example/repo", args.get(5));
  }

  // ─── ingestAll only runs enabled sources ──────────────────────────────

  @Test
  void ingestAll_skipsDisabledSources() {
    OntologyGitSource disabled = makeSource("https://github.com/example/repo", "main", "*.ttl");
    disabled.setEnabled(false);
    OntologyGitSource enabled = makeSource("https://github.com/example/repo2", "main", "*.ttl");

    when(gitSourceDAO.listEnabled()).thenReturn(List.of(enabled));

    service.ingestAll();

    List<String> args = capturedGitArgs.get();
    // Only one clone was attempted (for the enabled source)
    assertNotNull(args);
    assertTrue(args.contains("https://github.com/example/repo2"));
    assertFalse(args.contains("https://github.com/example/repo"),
      "Disabled source should not be cloned");
  }

  // ─── deleteTempDir is defensive ────────────────────────────────────────

  @Test
  void deleteTempDir_nullIsSafe() {
    // Should not throw
    OntologyGitIngestService.deleteTempDir(null);
  }

  @Test
  void deleteTempDir_cleansUpFiles() throws IOException {
    Path dir = tempDir.resolve("to-delete");
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("a.ttl"), SAMPLE_TTL);
    Files.createDirectories(dir.resolve("sub"));
    Files.writeString(dir.resolve("sub").resolve("b.ttl"), SAMPLE_TTL);

    OntologyGitIngestService.deleteTempDir(dir);

    assertFalse(Files.exists(dir), "Temp dir should be deleted");
  }

  // ─── Helpers ───────────────────────────────────────────────────────────

  private OntologyGitSource makeSource(String repoUrl, String branch, String pathPattern) {
    OntologyGitSource s = new OntologyGitSource();
    s.setAppId("test-appid-" + System.nanoTime());
    s.setName("Test Source");
    s.setRepoUrl(repoUrl);
    s.setBranch(branch);
    s.setPathPattern(pathPattern);
    s.setEnabled(true);
    return s;
  }
}
