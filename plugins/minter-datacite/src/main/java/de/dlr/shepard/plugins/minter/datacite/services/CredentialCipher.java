package de.dlr.shepard.plugins.minter.datacite.services;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * KIP1d — operator-managed-secret reversible cipher for the
 * DataCite Member password.
 *
 * <p>DataCite's REST API authenticates via HTTP Basic, so the
 * plaintext password must be available at mint-time. That rules out
 * one-way hashing (UH1a's pattern) for the password itself. The
 * compromise is AES-GCM with a per-instance key derived from the
 * {@code shepard.instance.id} property (which is itself per-deployment
 * and operator-controlled — see the
 * {@code instance.id} field in shepard's A1 bootstrap chain).
 *
 * <p>This is <b>not</b> a substitute for a real KMS. The threat
 * model is "an attacker reads {@code :DataciteMinterConfig} in
 * Neo4j" — the cipher means they cannot trivially impersonate the
 * shepard instance against DataCite. An attacker who can read both
 * Neo4j AND the JVM's application.properties recovers the plaintext,
 * which matches the "operator-managed secret" caveat documented in
 * the runbook.
 *
 * <p>Format of the cipher text: base64-url-no-padding of
 * {@code IV(12 bytes) || ciphertext(N bytes) || tag(16 bytes)}.
 * Prefix marker {@code "gcm1:"} so a future cipher upgrade can
 * recognise the legacy shape.
 */
public final class CredentialCipher {

  static final String CIPHER_PREFIX = "gcm1:";
  static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
  static final int IV_LENGTH_BYTES = 12;
  static final int TAG_LENGTH_BITS = 128;
  static final int KEY_LENGTH_BYTES = 32; // AES-256

  /**
   * Per-instance-id key. Derived by SHA-256 over the
   * {@code shepard.instance.id} (plus a fixed pepper) and truncated
   * to 32 bytes. The key never leaves this class.
   */
  private final SecretKeySpec keySpec;

  private final SecureRandom secureRandom = new SecureRandom();

  /** Build a cipher pinned to the given instance id. */
  public CredentialCipher(String instanceId) {
    if (instanceId == null || instanceId.isBlank()) {
      throw new IllegalArgumentException("instanceId must not be null/blank");
    }
    this.keySpec = new SecretKeySpec(deriveKey(instanceId), "AES");
  }

  /**
   * Encrypt a plaintext credential. Returns the {@code gcm1:}-prefixed
   * base64-url-no-padding ciphertext suitable for storing on
   * {@code DataciteMinterConfig.passwordCipher}.
   */
  public String encrypt(String plaintext) {
    if (plaintext == null) {
      throw new IllegalArgumentException("plaintext must not be null");
    }
    byte[] iv = new byte[IV_LENGTH_BYTES];
    secureRandom.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] out = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, out, 0, iv.length);
      System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
      return CIPHER_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM encrypt failed", e);
    }
  }

  /**
   * Decrypt a stored cipher back to the plaintext. Throws
   * {@link IllegalStateException} on tampering, format mismatch, or
   * key mismatch (e.g. the deployment's {@code shepard.instance.id}
   * changed since encryption).
   */
  public String decrypt(String cipherText) {
    if (cipherText == null || cipherText.isBlank()) {
      throw new IllegalArgumentException("cipherText must not be null/blank");
    }
    if (!cipherText.startsWith(CIPHER_PREFIX)) {
      throw new IllegalStateException("unrecognised cipher format (missing gcm1: prefix)");
    }
    byte[] raw = Base64.getUrlDecoder().decode(cipherText.substring(CIPHER_PREFIX.length()));
    if (raw.length < IV_LENGTH_BYTES + (TAG_LENGTH_BITS / 8)) {
      throw new IllegalStateException("cipher text too short");
    }
    byte[] iv = new byte[IV_LENGTH_BYTES];
    System.arraycopy(raw, 0, iv, 0, IV_LENGTH_BYTES);
    byte[] cipherBody = new byte[raw.length - IV_LENGTH_BYTES];
    System.arraycopy(raw, IV_LENGTH_BYTES, cipherBody, 0, cipherBody.length);
    try {
      Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
      cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] plaintext = cipher.doFinal(cipherBody);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM decrypt failed — credential tampered with or instance-id changed", e);
    }
  }

  /** Derive an AES-256 key from the instance id (SHA-256 + pepper). */
  static byte[] deriveKey(String instanceId) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update("shepard:KIP1d:datacite:".getBytes(StandardCharsets.UTF_8));
      md.update(instanceId.getBytes(StandardCharsets.UTF_8));
      byte[] full = md.digest();
      byte[] key = new byte[KEY_LENGTH_BYTES];
      System.arraycopy(full, 0, key, 0, KEY_LENGTH_BYTES);
      return key;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
