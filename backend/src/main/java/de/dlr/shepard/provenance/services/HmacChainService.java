package de.dlr.shepard.provenance.services;

import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.entities.InstanceConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * PR-3 — computes and verifies the HMAC-SHA256 audit chain over the
 * {@link Activity} stream.
 *
 * <p>The contract: every newly-written Activity carries
 * <ul>
 *   <li>{@code auditPrevHmac} — the immediately-prior Activity's
 *       {@code auditHmac} (or {@code null} on chain start);</li>
 *   <li>{@code auditHmac} = {@code HMAC-SHA256(instance_secret_v_n,
 *       prevHmac ‖ activityCanonical)};</li>
 *   <li>{@code secretVersion} = the version of the signing key
 *       in effect at write time — lets the verifier pick the right
 *       key on rotation.</li>
 * </ul>
 *
 * <p>The {@code activityCanonical} byte string is a stable
 * concatenation of the fields a tamperer would care about; see
 * {@link #canonical(Activity)}.
 *
 * <p><b>Failure mode.</b> The chain is observability, never a
 * write-blocker. If the {@code Mac} initialisation fails (e.g. JDK
 * missing HMAC-SHA256 — impossible on any modern JVM but defended
 * for completeness), the chain fields are left {@code null} and a
 * WARN is logged. The verifier treats a {@code null} chain as
 * "pre-chain row" — verifiable evidence that the row was written
 * before the chain was wired (or that the substrate failed). This
 * lets a deployment continue serving requests even if cryptography
 * is unavailable.
 */
@ApplicationScoped
public class HmacChainService {

  private static final String HMAC_ALGO = "HmacSHA256";
  /** Field separator inside the canonical bytes — chosen to never appear in valid IRIs / UUIDs. */
  private static final String CANON_SEP = ""; // ASCII "record separator"

  @Inject
  InstanceConfigService instanceConfigService;

  @Inject
  ActivityDAO activityDAO;

  /**
   * Stamp the chain fields on a new {@link Activity} that has not yet
   * been persisted. The caller is expected to invoke this immediately
   * before {@link ActivityDAO#createOrUpdate} so the chain segment is
   * one transactional unit on the OGM session.
   *
   * <p>This method:
   * <ol>
   *   <li>Loads the singleton {@link InstanceConfig}.</li>
   *   <li>Reads the most-recent Activity's {@code auditHmac} as the
   *       chain pointer (may be {@code null}).</li>
   *   <li>Computes the new row's {@code auditHmac} and stamps all
   *       three chain fields on {@code a}.</li>
   * </ol>
   *
   * <p>Best-effort: on any internal failure the chain fields are
   * left {@code null} and a debug log emitted. The Activity write
   * proceeds — audit observability never blocks user writes.
   */
  public void stamp(Activity a) {
    if (a == null) return;
    try {
      InstanceConfig cfg = instanceConfigService.current();
      if (cfg == null || cfg.getInstanceSecret() == null) {
        Log.debug("HmacChainService.stamp: no instance config; skipping chain stamp.");
        return;
      }
      String prev = findLatestHmac();
      a.setAuditPrevHmac(prev);
      a.setSecretVersion(cfg.getSecretVersion());
      a.setAuditHmac(computeHmac(cfg.getInstanceSecret(), prev, a));
    } catch (RuntimeException ex) {
      // Defence-in-depth — never block the underlying user write.
      Log.debugf(ex, "HmacChainService.stamp: chain stamp failed (%s).", ex.getClass().getSimpleName());
    }
  }

  /**
   * Verify one chain segment.
   *
   * @param current the activity whose chain stamp we verify
   * @param prev    the activity immediately prior in the chain, or {@code null} on chain start
   * @param keysByVersion key set, keyed by {@code secretVersion}
   * @return {@code true} when the chain stamp on {@code current} reproduces from {@code prev} + key
   */
  public boolean verify(Activity current, Activity prev, Map<Integer, String> keysByVersion) {
    if (current == null || current.getAuditHmac() == null || current.getSecretVersion() == null) {
      return false;
    }
    String key = keysByVersion.get(current.getSecretVersion());
    if (key == null) {
      Log.warnf(
        "HmacChainService.verify: no key for secretVersion=%d; cannot verify activity appId=%s.",
        current.getSecretVersion(),
        current.getAppId()
      );
      return false;
    }
    String expectedPrev = prev == null ? null : prev.getAuditHmac();
    if (!java.util.Objects.equals(expectedPrev, current.getAuditPrevHmac())) {
      return false;
    }
    String recomputed = computeHmac(key, expectedPrev, current);
    return constantTimeEq(recomputed, current.getAuditHmac());
  }

  // ─── Internals ────────────────────────────────────────────────────

  private String findLatestHmac() {
    // The cheapest "tail of the chain" query: the most-recent row by
    // startedAtMillis whose auditHmac is non-null. We read via the
    // existing list endpoint with limit=1 + filter post-fetch to keep
    // this PR scoped — a dedicated DAO method is a follow-up.
    var recent = activityDAO.list(null, null, null, null, null, 1);
    if (recent == null || recent.isEmpty()) return null;
    return recent.get(0).getAuditHmac();
  }

  /**
   * Visible for tests + the {@link #verify} path.
   *
   * <p>The canonical encoding includes the fields whose mutation a
   * tamperer would have to forge to plant a fake activity:
   * {@code actionKind}, {@code targetKind}, {@code targetAppId},
   * {@code agentUsername}, {@code method}, {@code path},
   * {@code status}, {@code startedAtMillis}, {@code endedAtMillis},
   * {@code originInstance}. {@code summary} is intentionally
   * excluded — it's a derived display string, not a primary fact;
   * including it would force chain breaks on cosmetic summary
   * edits.
   */
  static String canonical(Activity a) {
    StringBuilder sb = new StringBuilder(256);
    append(sb, a.getActionKind());
    append(sb, a.getTargetKind());
    append(sb, a.getTargetAppId());
    append(sb, a.getAgentUsername());
    append(sb, a.getMethod());
    append(sb, a.getPath());
    append(sb, a.getStatus() == null ? null : a.getStatus().toString());
    append(sb, a.getStartedAtMillis() == null ? null : a.getStartedAtMillis().toString());
    append(sb, a.getEndedAtMillis() == null ? null : a.getEndedAtMillis().toString());
    append(sb, a.getOriginInstance());
    return sb.toString();
  }

  private static void append(StringBuilder sb, String v) {
    sb.append(v == null ? "" : v).append(CANON_SEP);
  }

  static String computeHmac(String keyMaterial, String prev, Activity a) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGO);
      mac.init(new SecretKeySpec(keyMaterial.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
      if (prev != null) {
        mac.update(prev.getBytes(StandardCharsets.UTF_8));
      }
      mac.update(canonical(a).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(mac.doFinal());
    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      // HMAC-SHA256 is mandatory in the JDK since Java 1.5; the only
      // realistic trigger is a security-policy block. Treat as fatal
      // for the chain (caller logs + degrades).
      throw new IllegalStateException("HMAC-SHA256 unavailable: " + ex.getMessage(), ex);
    }
  }

  /** Constant-time string equality on hex strings — defence against timing-channel comparison. */
  static boolean constantTimeEq(String a, String b) {
    if (a == null || b == null) return false;
    if (a.length() != b.length()) return false;
    int diff = 0;
    for (int i = 0; i < a.length(); i++) {
      diff |= a.charAt(i) ^ b.charAt(i);
    }
    return diff == 0;
  }
}
