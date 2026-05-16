package de.dlr.shepard.auth.bootstrap;

import de.dlr.shepard.common.neo4j.NeoConnector;
import io.quarkus.logging.Log;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Startup hook that generates a one-shot bootstrap token when shepard
 * has zero instance-admins. Designed in {@code aidocs/51 §5.1}.
 *
 * <p>Runs from {@code ShepardMain.init} <b>after</b> migrations
 * succeed and after {@code NeoConnector.connect()}. Three pieces of
 * state matter:
 *
 * <ol>
 *   <li>Bootstrap state — true iff no user has a {@code :HAS_ROLE}
 *       edge to the {@code instance-admin} {@code Role} <b>and</b> no
 *       API key carries {@code instance-admin} in its {@code roles}
 *       set. Both checks done via Cypher.
 *   <li>The token — UUID v4 (sufficient entropy; matches Vault's
 *       unseal-key shape per the design).
 *   <li>Persistence — a {@code (:BootstrapState {tokenHash})} node
 *       records the SHA-256 of the token so consumption can verify.
 *       Re-runs in bootstrap state regenerate (and replace) the token,
 *       invalidating any leaked old one.
 * </ol>
 *
 * <p>The token's plaintext lives <b>only</b> on disk at
 * {@code shepard.bootstrap.token-path} (default
 * {@code /opt/shepard/.bootstrap-token}), with mode {@code 0600} on
 * POSIX systems. The path is configurable so containers / non-root
 * deployments / CI can pick a writable directory.
 */
public class BootstrapTokenInitializer {

  static final String TOKEN_PATH_PROPERTY = "shepard.bootstrap.token-path";
  static final String DEFAULT_TOKEN_PATH = "/opt/shepard/.bootstrap-token";

  /**
   * Runs the initializer, idempotent. Returns the path the token was
   * written to (for tests + the startup log) or {@code null} if no
   * token was needed (an instance-admin already exists, or the
   * filesystem is unwritable).
   */
  public Path runIfNeeded() {
    if (instanceAdminExists()) {
      Log.debug("BOOTSTRAP: instance-admin exists — skipping bootstrap-token generation.");
      return null;
    }

    String token = UUID.randomUUID().toString();
    String hash = sha256Hex(token);

    // Persist the hash before writing the file: if the file write
    // fails, the next run regenerates cleanly — no half-state where a
    // file exists but the graph doesn't recognise it.
    persistTokenHash(hash);

    Path target = resolveTokenPath();
    try {
      writeTokenFile(target, token);
      Log.warnf(
        "BOOTSTRAP: shepard has no instance-admin yet. The bootstrap token has been written to '%s' " +
        "(mode 0600 on POSIX). Run `shepard-admin instance-admin bootstrap --user <username>` " +
        "to grant the role; the token is consumed on first use.",
        target
      );
      return target;
    } catch (IOException e) {
      Log.errorf(
        e,
        "BOOTSTRAP: failed to write bootstrap token to '%s'. Configure '%s' to point to a writable path. " +
        "The :BootstrapState node persists; the next start will regenerate the token.",
        target,
        TOKEN_PATH_PROPERTY
      );
      return null;
    }
  }

  static boolean instanceAdminExists() {
    var session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return false;
    var result = session.query(
      "OPTIONAL MATCH (:User)-[:HAS_ROLE]->(role:Role {name: 'instance-admin'}) " +
      "WITH count(role) AS edgeCount " +
      "OPTIONAL MATCH (k:ApiKey) WHERE 'instance-admin' IN coalesce(k.roles, []) " +
      "WITH edgeCount, count(k) AS apiKeyCount " +
      "RETURN edgeCount + apiKeyCount AS total",
      Map.of()
    );
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return false;
    Object total = it.next().get("total");
    return total instanceof Number n && n.longValue() > 0;
  }

  static void persistTokenHash(String hash) {
    var session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) {
      throw new IllegalStateException("Neo4j session not initialised");
    }
    // Replace any prior :BootstrapState — only one outstanding token at a time.
    session.query("MATCH (b:BootstrapState) DETACH DELETE b", Map.of());
    session.query(
      "CREATE (b:BootstrapState {tokenHash: $h, createdAt: $ts})",
      Map.of("h", hash, "ts", new Date().getTime())
    );
  }

  static Path resolveTokenPath() {
    String configured = ConfigProvider.getConfig()
      .getOptionalValue(TOKEN_PATH_PROPERTY, String.class)
      .filter(s -> !s.isBlank())
      .orElse(DEFAULT_TOKEN_PATH);
    return Path.of(configured);
  }

  static void writeTokenFile(Path target, String token) throws IOException {
    if (target.getParent() != null) {
      Files.createDirectories(target.getParent());
    }
    Files.writeString(target, token + "\n", StandardCharsets.UTF_8);
    if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
      Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
      try {
        Files.setPosixFilePermissions(target, perms);
      } catch (UnsupportedOperationException ignored) {
        // Non-POSIX filesystem (Windows). Leave default ACLs.
      }
    }
  }

  /** SHA-256 hex digest of the input. Package-private so the bootstrap endpoint can verify. */
  public static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
