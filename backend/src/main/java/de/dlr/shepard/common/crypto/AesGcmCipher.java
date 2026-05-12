package de.dlr.shepard.common.crypto;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM encrypt/decrypt utility. Each invocation of {@link #encrypt}
 * generates a fresh 12-byte random IV; the output is
 * {@code base64(IV ‖ ciphertext+tag)}, making every ciphertext distinct even
 * for identical plaintexts — important for PAT storage where the same token
 * might be re-entered.
 *
 * <p>The choice of AES-GCM over CBC+HMAC is deliberate: authenticated
 * encryption detects ciphertext tampering at decrypt time (the JCA
 * layer throws {@link AEADBadTagException}), which we wrap as
 * {@link IllegalArgumentException} so callers don't need to import JCA
 * internals.
 */
public final class AesGcmCipher {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final SecureRandom RNG = new SecureRandom();

  private AesGcmCipher() {}

  /**
   * @param plaintext UTF-8 string to encrypt.
   * @param key       32-byte AES-256 key.
   * @return base64-encoded {@code IV ‖ ciphertext+GCM-tag}.
   */
  public static String encrypt(String plaintext, byte[] key) {
    try {
      byte[] iv = new byte[IV_BYTES];
      RNG.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

      byte[] out = new byte[IV_BYTES + ciphertext.length];
      System.arraycopy(iv, 0, out, 0, IV_BYTES);
      System.arraycopy(ciphertext, 0, out, IV_BYTES, ciphertext.length);
      return Base64.getEncoder().encodeToString(out);
    } catch (Exception e) {
      throw new IllegalStateException("AES-GCM encrypt failed", e);
    }
  }

  /**
   * @param encoded base64-encoded {@code IV ‖ ciphertext+GCM-tag} as produced by
   *                {@link #encrypt}.
   * @param key     32-byte AES-256 key.
   * @return plaintext string.
   * @throws IllegalArgumentException if the ciphertext is tampered (authentication tag failure)
   *                                  or the encoded value is malformed.
   */
  public static String decrypt(String encoded, byte[] key) {
    try {
      byte[] data = Base64.getDecoder().decode(encoded);
      if (data.length <= IV_BYTES) {
        throw new IllegalArgumentException("Encoded value is too short to contain IV + ciphertext");
      }

      byte[] iv = new byte[IV_BYTES];
      System.arraycopy(data, 0, iv, 0, IV_BYTES);
      byte[] ciphertext = new byte[data.length - IV_BYTES];
      System.arraycopy(data, IV_BYTES, ciphertext, 0, ciphertext.length);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
      byte[] plainBytes = cipher.doFinal(ciphertext);
      return new String(plainBytes, java.nio.charset.StandardCharsets.UTF_8);
    } catch (AEADBadTagException e) {
      throw new IllegalArgumentException("AES-GCM authentication tag verification failed — ciphertext may be tampered", e);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("AES-GCM decrypt failed", e);
    }
  }
}
