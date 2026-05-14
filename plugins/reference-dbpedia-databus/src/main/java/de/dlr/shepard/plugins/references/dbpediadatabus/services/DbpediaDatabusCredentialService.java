package de.dlr.shepard.plugins.references.dbpediadatabus.services;

import de.dlr.shepard.common.crypto.AesGcmCipher;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * REF1c — encrypts / decrypts / fingerprints the OAuth client secret
 * stored on {@code :DbpediaDatabusConfig}. Same shape as
 * {@code GitCredentialService} (G1-cred): the encryption key is
 * sourced from {@code shepard.secrets.encryption-key}. When the key
 * is absent the credential surfaces are disabled.
 *
 * <p>Plaintext handling:
 * <ul>
 *   <li>Plaintext accepted once at the POST credential endpoint,
 *       AES-GCM-encrypted, discarded.</li>
 *   <li>Never logged in plaintext; only the fingerprint (first 8
 *       hex chars of SHA-256(cipher)) is admin-displayed.</li>
 * </ul>
 */
@ApplicationScoped
public class DbpediaDatabusCredentialService {

  public static final int FINGERPRINT_LENGTH = 8;

  @ConfigProperty(name = "shepard.secrets.encryption-key")
  Optional<String> encryptionKey;

  public DbpediaDatabusCredentialService() {}

  DbpediaDatabusCredentialService(String encryptionKeyBase64) {
    this.encryptionKey = Optional.ofNullable(encryptionKeyBase64);
  }

  public boolean encryptionAvailable() {
    return resolveKey() != null;
  }

  public String encrypt(String plaintext) {
    byte[] key = resolveKey();
    if (key == null) {
      throw new IllegalStateException(
        "shepard.secrets.encryption-key is not configured — DBpedia Databus credential storage disabled"
      );
    }
    if (plaintext == null || plaintext.isEmpty()) {
      throw new IllegalArgumentException("OAuth client secret must be non-empty");
    }
    return AesGcmCipher.encrypt(plaintext, key);
  }

  public Optional<String> decrypt(String cipher) {
    if (cipher == null || cipher.isBlank()) return Optional.empty();
    byte[] key = resolveKey();
    if (key == null) return Optional.empty();
    try {
      return Optional.of(AesGcmCipher.decrypt(cipher, key));
    } catch (IllegalArgumentException e) {
      Log.warnf(e, "REF1c: failed to decrypt DBpedia Databus OAuth client secret");
      return Optional.empty();
    }
  }

  public String fingerprint(String cipher) {
    if (cipher == null || cipher.isBlank()) return null;
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(cipher.getBytes(StandardCharsets.UTF_8));
      String hex = HexFormat.of().formatHex(digest);
      return hex.substring(0, Math.min(FINGERPRINT_LENGTH, hex.length()));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private byte[] resolveKey() {
    if (encryptionKey == null || encryptionKey.isEmpty() || encryptionKey.get().isBlank()) return null;
    try {
      return Base64.getDecoder().decode(encryptionKey.get().trim());
    } catch (IllegalArgumentException e) {
      Log.error("shepard.secrets.encryption-key is not valid base64 — treating as absent");
      return null;
    }
  }
}
