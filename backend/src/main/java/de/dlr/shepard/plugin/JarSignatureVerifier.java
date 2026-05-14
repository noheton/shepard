package de.dlr.shepard.plugin;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarException;
import java.util.jar.JarFile;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * PM1b2 — verifies a plugin JAR's signature against a configured set
 * of trust anchors before the {@link PluginRegistry} loads the
 * plugin's classes.
 *
 * <p>Three outcomes per verification:
 * <ul>
 *   <li>{@link VerifyResult.Status#TRUSTED} — JAR is signed and at
 *       least one signer's certificate matches a configured trust
 *       anchor.</li>
 *   <li>{@link VerifyResult.Status#UNSIGNED} — JAR carries no signed
 *       entries.</li>
 *   <li>{@link VerifyResult.Status#UNTRUSTED} — JAR is signed but
 *       none of the signers match a configured trust anchor (or the
 *       truststore is empty).</li>
 * </ul>
 *
 * <p>Behaviour matrix (driven by
 * {@code shepard.plugins.signing.required}):
 * <table>
 *   <caption>Signature verification outcomes</caption>
 *   <tr><th>required</th><th>JAR</th><th>Action</th></tr>
 *   <tr><td>{@code false} (default)</td><td>any</td><td>Verifier runs,
 *       result is logged at INFO, plugin registers regardless.</td></tr>
 *   <tr><td>{@code true}</td><td>TRUSTED</td><td>Plugin registers
 *       normally.</td></tr>
 *   <tr><td>{@code true}</td><td>UNSIGNED</td><td>Plugin enters FAILED
 *       state with {@code plugin.signature.unsigned}.</td></tr>
 *   <tr><td>{@code true}</td><td>UNTRUSTED</td><td>Plugin enters FAILED
 *       state with {@code plugin.signature.untrusted}.</td></tr>
 * </table>
 *
 * <p>Default posture is {@code required=false} (backward-compatible
 * with PM1a — UH1a / KIP / minter-local plugins ship unsigned today;
 * making this gate active by default would break the upgrade path
 * the {@code aidocs/34} ledger promises). An operator opts in by
 * setting the four {@code shepard.plugins.signing.*} keys.
 *
 * <p>Trust-anchor configuration uses the JDK keystore APIs: an
 * operator points {@code shepard.plugins.signing.truststore.path}
 * at a JKS / PKCS12 truststore file and provides the password via
 * {@code shepard.plugins.signing.truststore.password}. Every
 * certificate alias in the truststore is loaded; a JAR is TRUSTED if
 * any of its signer certificates {@link Object#equals(Object) equals}
 * one of those certificates.
 *
 * <p>Implementation note: uses {@link JarFile} with the {@code verify}
 * flag set, which makes the JDK parse {@code META-INF/MANIFEST.MF} +
 * {@code META-INF/*.SF} + {@code META-INF/*.RSA|DSA|EC} blocks
 * itself. Tampered JARs (manifest digest mismatch) throw a
 * {@link SecurityException} from {@link JarFile#getInputStream} — we
 * surface that as a {@link JarSignatureException} on
 * {@link #verify(Path)}.
 *
 * <p>Lives in core (not a plugin) — part of the runtime SPI-registry
 * itself, one of CLAUDE.md's plugin-first exceptions.
 */
@ApplicationScoped
public class JarSignatureVerifier {

  /** Config key — fail discovery for unsigned/untrusted JARs. */
  public static final String CONFIG_SIGNING_REQUIRED = "shepard.plugins.signing.required";

  /**
   * CSV of trust-anchor identifiers — surfaced for forward compat
   * (e.g. PEM-on-disk anchors); the truststore path takes precedence
   * when both are set.
   */
  public static final String CONFIG_TRUST_ANCHORS = "shepard.plugins.signing.trust-anchors";

  /** Filesystem path to a JKS / PKCS12 truststore. */
  public static final String CONFIG_TRUSTSTORE_PATH = "shepard.plugins.signing.truststore.path";

  /** Truststore password — blank string means "no password". */
  public static final String CONFIG_TRUSTSTORE_PASSWORD = "shepard.plugins.signing.truststore.password";

  @ConfigProperty(name = CONFIG_SIGNING_REQUIRED, defaultValue = "false")
  boolean signingRequired;

  @ConfigProperty(name = CONFIG_TRUST_ANCHORS, defaultValue = "")
  String trustAnchorsCsv;

  @ConfigProperty(name = CONFIG_TRUSTSTORE_PATH, defaultValue = "")
  String truststorePath;

  @ConfigProperty(name = CONFIG_TRUSTSTORE_PASSWORD, defaultValue = "")
  String truststorePassword;

  /**
   * Loaded trust anchors — populated lazily on first {@link #verify}
   * call (or eagerly via {@link #init()} on CDI scope). Empty set
   * means the verifier behaves as "no trust anchors configured" —
   * every signed JAR will land in UNTRUSTED.
   */
  private volatile Set<Certificate> trustedCerts = Collections.emptySet();

  private volatile boolean initialised = false;

  @PostConstruct
  void init() {
    // Eager load — surfaces a malformed truststore at startup rather
    // than on first verify call (more operator-friendly).
    loadTrustAnchorsIfNeeded();
  }

  /**
   * Whether the operator has opted in to mandatory signing. Useful
   * for the registry: when {@code false}, a non-TRUSTED result is
   * logged but plugins still register.
   */
  public boolean isSigningRequired() {
    return signingRequired;
  }

  /**
   * Verify a plugin JAR's signature against the configured trust
   * anchors.
   *
   * @return a {@link VerifyResult} describing the outcome.
   * @throws JarSignatureException if the JAR cannot be parsed (e.g.
   *         tampered manifest, IO error reading the file).
   */
  public VerifyResult verify(Path jarPath) {
    loadTrustAnchorsIfNeeded();
    if (jarPath == null || !Files.exists(jarPath)) {
      throw new JarSignatureException("JAR not found: " + jarPath);
    }
    // verify=true asks the JDK to parse signature blocks during
    // getInputStream; that's where SecurityException surfaces if the
    // manifest digest doesn't match. We must read every entry to
    // populate codeSigners.
    try (JarFile jar = new JarFile(jarPath.toFile(), true)) {
      List<X509Certificate> signers = new ArrayList<>();
      boolean foundSignedEntry = false;
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        // Skip directory entries and the META-INF housekeeping
        // entries themselves — signatures live ON the files they
        // sign, not on the signature blocks.
        if (entry.isDirectory()) {
          continue;
        }
        String name = entry.getName();
        if (name.startsWith("META-INF/") && (
            name.endsWith(".SF") || name.endsWith(".RSA") ||
            name.endsWith(".DSA") || name.endsWith(".EC") ||
            "META-INF/MANIFEST.MF".equals(name))) {
          continue;
        }
        // Drain the stream so getCertificates() / getCodeSigners()
        // become populated. Tampered entries throw SecurityException
        // here — the JDK's contract.
        try (InputStream in = jar.getInputStream(entry)) {
          byte[] buf = new byte[8192];
          while (in.read(buf) > 0) {
            // discard — we only care about side-effects.
          }
        }
        Certificate[] certs = entry.getCertificates();
        if (certs != null && certs.length > 0) {
          foundSignedEntry = true;
          for (Certificate c : certs) {
            if (c instanceof X509Certificate x509 && !signers.contains(x509)) {
              signers.add(x509);
            }
          }
        }
      }
      if (!foundSignedEntry) {
        return new VerifyResult(VerifyResult.Status.UNSIGNED, List.of(), "JAR carries no signed entries");
      }
      // Signed — is any signer trusted?
      Set<Certificate> snapshot = trustedCerts;
      for (X509Certificate signer : signers) {
        if (snapshot.contains(signer)) {
          return new VerifyResult(
            VerifyResult.Status.TRUSTED,
            List.copyOf(signers),
            "Signed by trusted publisher: " + signer.getSubjectX500Principal().getName()
          );
        }
      }
      return new VerifyResult(
        VerifyResult.Status.UNTRUSTED,
        List.copyOf(signers),
        signers.isEmpty()
          ? "JAR signed but no signer certificates could be parsed"
          : "JAR signed by " + signers.get(0).getSubjectX500Principal().getName() + " — not a configured trust anchor"
      );
    } catch (SecurityException ex) {
      // Tampered JAR — JDK signature-block parser raised on
      // getInputStream when the manifest digest didn't match.
      throw new JarSignatureException(
        "JAR signature verification failed for " + jarPath + ": " + ex.getMessage(),
        ex
      );
    } catch (JarException ex) {
      throw new JarSignatureException(
        "Malformed JAR " + jarPath + ": " + ex.getMessage(),
        ex
      );
    } catch (IOException ex) {
      throw new JarSignatureException(
        "I/O error reading " + jarPath + ": " + ex.getMessage(),
        ex
      );
    }
  }

  /**
   * Re-read the truststore configuration. Test-friendly hook — lets a
   * unit test mutate {@code truststorePath} via reflection and call
   * {@code reload()} to re-load anchors without spinning up a fresh
   * CDI scope. Idempotent.
   */
  void reload() {
    initialised = false;
    loadTrustAnchorsIfNeeded();
  }

  private void loadTrustAnchorsIfNeeded() {
    if (initialised) {
      return;
    }
    synchronized (this) {
      if (initialised) {
        return;
      }
      Set<Certificate> loaded = new HashSet<>();
      if (truststorePath != null && !truststorePath.isBlank()) {
        Path p = Paths.get(truststorePath);
        if (!Files.exists(p)) {
          Log.warnf(
            "PM1b2: truststore path '%s' does not exist — JAR signature verification will report UNTRUSTED for every signed JAR (operator misconfiguration; check shepard.plugins.signing.truststore.path)",
            truststorePath
          );
        } else {
          char[] pw = (truststorePassword == null || truststorePassword.isBlank())
            ? new char[0]
            : truststorePassword.toCharArray();
          // Try JKS first; fall back to PKCS12 (the default in
          // recent JDKs). Some operators ship one, some the other.
          KeyStore ks = tryLoadKeystore(p, pw, "JKS");
          if (ks == null) {
            ks = tryLoadKeystore(p, pw, "PKCS12");
          }
          if (ks != null) {
            try {
              Enumeration<String> aliases = ks.aliases();
              while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = ks.getCertificate(alias);
                if (cert != null) {
                  loaded.add(cert);
                }
              }
            } catch (KeyStoreException ex) {
              Log.warnf(
                ex,
                "PM1b2: truststore '%s' could not be enumerated — signature verification will report UNTRUSTED for every signed JAR",
                truststorePath
              );
            }
          } else {
            Log.warnf(
              "PM1b2: truststore '%s' could not be loaded as JKS or PKCS12 — signature verification will report UNTRUSTED for every signed JAR",
              truststorePath
            );
          }
        }
      }
      this.trustedCerts = Set.copyOf(loaded);
      this.initialised = true;
      if (!loaded.isEmpty()) {
        Log.infof("PM1b2: loaded %d trust anchor(s) from %s", loaded.size(), truststorePath);
      } else if (signingRequired) {
        Log.warnf(
          "PM1b2: shepard.plugins.signing.required=true but no trust anchors loaded — every signed plugin JAR will land in FAILED state. Set shepard.plugins.signing.truststore.path to a JKS/PKCS12 truststore."
        );
      }
    }
  }

  private KeyStore tryLoadKeystore(Path p, char[] pw, String type) {
    try (InputStream in = Files.newInputStream(p)) {
      KeyStore ks = KeyStore.getInstance(type);
      ks.load(in, pw);
      return ks;
    } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException ex) {
      Log.debugf(ex, "PM1b2: truststore '%s' did not load as %s — will try next type", p, type);
      return null;
    }
  }

  /**
   * Outcome of a JAR signature verification call. Carries the
   * {@link Status}, the list of distinct X.509 signer certificates the
   * verifier could parse (empty for UNSIGNED; possibly multiple for
   * cross-signed JARs), and a human-readable message that the
   * registry surfaces in the {@code failureMessage} when applicable.
   */
  public record VerifyResult(Status status, List<X509Certificate> signers, String message) {
    /** Verification outcomes. */
    public enum Status {
      /** Signed by at least one configured trust anchor. */
      TRUSTED,
      /** No signed entries — the JAR was packaged without {@code jarsigner}. */
      UNSIGNED,
      /** Signed but no signer matched a configured trust anchor. */
      UNTRUSTED,
    }
  }
}
