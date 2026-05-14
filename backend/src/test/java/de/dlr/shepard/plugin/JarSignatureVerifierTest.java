package de.dlr.shepard.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;
import jdk.security.jarsigner.JarSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PM1b2 — covers {@link JarSignatureVerifier}.
 *
 * <p>Each test invokes the JDK's {@code keytool} via
 * {@link ProcessBuilder} to generate a key + self-signed cert, then
 * uses {@link JarSigner} (the public JDK API the {@code jarsigner}
 * CLI is built on) to sign a JAR. The verifier is exercised
 * reflectively without booting CDI / @ConfigProperty injection —
 * matching the {@code PluginRegistryTest} fixture's posture.
 *
 * <p>Tests deliberately avoid {@code sun.security.tools.*} internal
 * classes so we don't need {@code --add-exports} flags in the
 * surefire {@code argLine}.
 */
class JarSignatureVerifierTest {

  @TempDir
  Path tempDir;

  JarSignatureVerifier verifier;

  @BeforeEach
  void setUp() throws Exception {
    verifier = new JarSignatureVerifier();
    // Reflective defaults — fields would otherwise stay null since
    // we're not booting CDI / @ConfigProperty injection.
    setField("signingRequired", false);
    setField("trustAnchorsCsv", "");
    setField("truststorePath", "");
    setField("truststorePassword", "");
  }

  // ─── 1. Unsigned JAR ──────────────────────────────────────────

  @Test
  void unsignedJar_reportsUNSIGNED() throws Exception {
    Path jar = makeUnsignedJar(tempDir.resolve("unsigned.jar"));
    verifier.reload();

    JarSignatureVerifier.VerifyResult r = verifier.verify(jar);

    assertThat(r.status()).isEqualTo(JarSignatureVerifier.VerifyResult.Status.UNSIGNED);
    assertThat(r.signers()).isEmpty();
  }

  // ─── 2. Self-signed JAR, cert not in truststore ───────────────

  @Test
  void selfSignedJar_noTruststore_reportsUNTRUSTED() throws Exception {
    KeyAndCert kc = mintKeyAndCert(tempDir.resolve("k1.jks"), "alias1", "CN=Random Self-Signed");
    Path jar = makeSignedJar(tempDir.resolve("selfsigned.jar"), kc);
    verifier.reload();

    JarSignatureVerifier.VerifyResult r = verifier.verify(jar);

    assertThat(r.status()).isEqualTo(JarSignatureVerifier.VerifyResult.Status.UNTRUSTED);
    assertThat(r.signers()).isNotEmpty();
  }

  // ─── 3. Signed by trust anchor → TRUSTED ──────────────────────

  @Test
  void signedByTrustAnchor_reportsTRUSTED() throws Exception {
    KeyAndCert kc = mintKeyAndCert(tempDir.resolve("k2.jks"), "alias2", "CN=Trusted Publisher");
    Path jar = makeSignedJar(tempDir.resolve("trusted.jar"), kc);
    Path ts = makeTruststore(tempDir.resolve("ts.jks"), "anchor", kc.cert, "changeit");
    setField("truststorePath", ts.toString());
    setField("truststorePassword", "changeit");
    verifier.reload();

    JarSignatureVerifier.VerifyResult r = verifier.verify(jar);

    assertThat(r.status()).isEqualTo(JarSignatureVerifier.VerifyResult.Status.TRUSTED);
    assertThat(r.signers()).hasSize(1);
    assertThat(r.signers().get(0).getSubjectX500Principal().getName())
      .contains("Trusted Publisher");
  }

  // ─── 4. Tampered JAR → throws JarSignatureException ───────────

  @Test
  void tamperedJar_throwsJarSignatureException() throws Exception {
    KeyAndCert kc = mintKeyAndCert(tempDir.resolve("k3.jks"), "alias3", "CN=Doomed");
    Path jar = makeSignedJar(tempDir.resolve("doomed.jar"), kc);
    tamperJar(jar);
    verifier.reload();

    assertThatThrownBy(() -> verifier.verify(jar))
      .isInstanceOf(JarSignatureException.class);
  }

  // ─── 5. Empty truststore + signed JAR ─────────────────────────

  @Test
  void emptyTruststore_signedJar_reportsUNTRUSTED() throws Exception {
    KeyAndCert kc = mintKeyAndCert(tempDir.resolve("k4.jks"), "alias4", "CN=Whoever");
    Path jar = makeSignedJar(tempDir.resolve("signed.jar"), kc);
    Path ts = makeEmptyTruststore(tempDir.resolve("empty.jks"), "changeit");
    setField("truststorePath", ts.toString());
    setField("truststorePassword", "changeit");
    verifier.reload();

    JarSignatureVerifier.VerifyResult r = verifier.verify(jar);

    assertThat(r.status()).isEqualTo(JarSignatureVerifier.VerifyResult.Status.UNTRUSTED);
  }

  // ─── 6. Default config (signing.required=false) ───────────────

  @Test
  void defaultConfig_signingNotRequired_returnsResultButNothingFatal() throws Exception {
    // Default posture: required=false. Verifier still runs and
    // returns a result; downstream registry decides whether to act.
    Path jar = makeUnsignedJar(tempDir.resolve("default.jar"));
    verifier.reload();

    JarSignatureVerifier.VerifyResult r = verifier.verify(jar);

    assertThat(r.status()).isEqualTo(JarSignatureVerifier.VerifyResult.Status.UNSIGNED);
    assertThat(verifier.isSigningRequired()).isFalse();
  }

  // ─── 7. Invalid truststore path → fail-soft WARN ──────────────

  @Test
  void invalidTruststorePath_failsSoft() throws Exception {
    setField("truststorePath", tempDir.resolve("does-not-exist.jks").toString());
    setField("truststorePassword", "anything");
    verifier.reload();

    KeyAndCert kc = mintKeyAndCert(tempDir.resolve("k5.jks"), "alias5", "CN=Whoever");
    Path jar = makeSignedJar(tempDir.resolve("signed2.jar"), kc);
    JarSignatureVerifier.VerifyResult r = verifier.verify(jar);

    assertThat(r.status()).isEqualTo(JarSignatureVerifier.VerifyResult.Status.UNTRUSTED);
  }

  // ─── 8. isSigningRequired() reflects config ───────────────────

  @Test
  void isSigningRequired_followsConfigField() throws Exception {
    assertThat(verifier.isSigningRequired()).isFalse();
    setField("signingRequired", true);
    assertThat(verifier.isSigningRequired()).isTrue();
  }

  // ─── 9. Two-anchor truststore: one matches → TRUSTED ──────────

  @Test
  void multipleTrustAnchors_anyMatch_reportsTRUSTED() throws Exception {
    KeyAndCert kcA = mintKeyAndCert(tempDir.resolve("kA.jks"), "aliasA", "CN=Anchor A");
    KeyAndCert kcB = mintKeyAndCert(tempDir.resolve("kB.jks"), "aliasB", "CN=Anchor B");
    // Sign with anchor A; truststore contains BOTH A and B.
    Path jar = makeSignedJar(tempDir.resolve("multi.jar"), kcA);
    Path ts = tempDir.resolve("multi-ts.jks");
    KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(null, "changeit".toCharArray());
    ks.setCertificateEntry("a", kcA.cert);
    ks.setCertificateEntry("b", kcB.cert);
    try (OutputStream out = Files.newOutputStream(ts)) {
      ks.store(out, "changeit".toCharArray());
    }
    setField("truststorePath", ts.toString());
    setField("truststorePassword", "changeit");
    verifier.reload();

    JarSignatureVerifier.VerifyResult r = verifier.verify(jar);

    assertThat(r.status()).isEqualTo(JarSignatureVerifier.VerifyResult.Status.TRUSTED);
  }

  // ─── helpers ──────────────────────────────────────────────────

  private record KeyAndCert(PrivateKey key, X509Certificate cert) {}

  /**
   * Generate a fresh keystore via {@code keytool}, then load the
   * private key + cert out of it. Avoids touching internal JDK
   * classes ({@code sun.security.tools.*} et al.).
   */
  private KeyAndCert mintKeyAndCert(Path ksPath, String alias, String dn) throws Exception {
    String password = "changeit";
    int rc = new ProcessBuilder(
      "keytool", "-genkeypair",
      "-keyalg", "RSA",
      "-keysize", "2048",
      "-validity", "365",
      "-dname", dn,
      "-alias", alias,
      "-storetype", "JKS",
      "-keystore", ksPath.toString(),
      "-storepass", password,
      "-keypass", password
    ).inheritIO().start().waitFor();
    if (rc != 0) {
      throw new IllegalStateException("keytool returned " + rc);
    }
    KeyStore ks = KeyStore.getInstance("JKS");
    try (var in = Files.newInputStream(ksPath)) {
      ks.load(in, password.toCharArray());
    }
    PrivateKey key = (PrivateKey) ks.getKey(alias, password.toCharArray());
    Certificate cert = ks.getCertificate(alias);
    return new KeyAndCert(key, (X509Certificate) cert);
  }

  /**
   * Build an unsigned JAR with one content entry. The entry is
   * STORED (uncompressed) so {@link #tamperJar(Path)} can find the
   * payload bytes verbatim in the file stream.
   */
  private Path makeUnsignedJar(Path path) throws IOException {
    Manifest m = new Manifest();
    m.getMainAttributes().putValue("Manifest-Version", "1.0");
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(path), m)) {
      byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
      JarEntry e = new JarEntry("hello.txt");
      e.setMethod(JarEntry.STORED);
      e.setSize(payload.length);
      e.setCompressedSize(payload.length);
      java.util.zip.CRC32 crc = new java.util.zip.CRC32();
      crc.update(payload);
      e.setCrc(crc.getValue());
      out.putNextEntry(e);
      out.write(payload);
      out.closeEntry();
    }
    return path;
  }

  /** Sign an unsigned JAR with the given key/cert via {@link JarSigner}. */
  private Path makeSignedJar(Path path, KeyAndCert kc) throws Exception {
    Path unsigned = makeUnsignedJar(Files.createTempFile(tempDir, "u-", ".jar"));
    CertPath certPath = CertificateFactory.getInstance("X.509")
      .generateCertPath(List.of(kc.cert));
    JarSigner signer = new JarSigner.Builder(kc.key, certPath)
      .digestAlgorithm("SHA-256")
      .signatureAlgorithm("SHA256withRSA")
      .signerName("SIGNER")
      .build();
    try (
      ZipFile in = new ZipFile(unsigned.toFile());
      OutputStream os = Files.newOutputStream(path)
    ) {
      signer.sign(in, os);
    }
    return path;
  }

  /** Build a JKS truststore with one cert under alias. */
  private Path makeTruststore(Path path, String alias, X509Certificate cert, String password)
    throws Exception {
    KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(null, password.toCharArray());
    ks.setCertificateEntry(alias, cert);
    try (OutputStream out = Files.newOutputStream(path)) {
      ks.store(out, password.toCharArray());
    }
    return path;
  }

  /** Build an empty JKS truststore. */
  private Path makeEmptyTruststore(Path path, String password) throws Exception {
    KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(null, password.toCharArray());
    try (OutputStream out = Files.newOutputStream(path)) {
      ks.store(out, password.toCharArray());
    }
    return path;
  }

  /**
   * Tamper a signed JAR by editing the content of one of its signed
   * entries directly in the bytestream. The JDK's signature
   * verifier raises {@link SecurityException} on
   * {@code JarFile.getInputStream} when the entry's actual digest
   * doesn't match the {@code .SF} block — which the verifier wraps
   * as {@link JarSignatureException}.
   *
   * <p>We replace one byte inside {@code hello.txt}'s zip payload.
   * Because we don't re-sign the JAR, the digest will mismatch.
   */
  private void tamperJar(Path jarPath) throws IOException {
    byte[] all = Files.readAllBytes(jarPath);
    byte[] needle = "hello world".getBytes(StandardCharsets.UTF_8);
    int idx = indexOf(all, needle);
    if (idx < 0) {
      throw new IllegalStateException("Couldn't locate hello world payload to tamper");
    }
    all[idx] = (byte) 'X'; // change "hello" → "Xello"
    Files.write(jarPath, all, StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static int indexOf(byte[] haystack, byte[] needle) {
    for (int i = 0; i <= haystack.length - needle.length; i++) {
      boolean match = true;
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          match = false;
          break;
        }
      }
      if (match) {
        return i;
      }
    }
    return -1;
  }

  private void setField(String name, Object value) throws Exception {
    var field = verifier.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(verifier, value);
  }
}
