package de.dlr.shepard.common.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmCipherTest {

  private static final byte[] KEY_32 = new byte[32];

  static {
    new SecureRandom().nextBytes(KEY_32);
  }

  @Test
  void roundTripRestoresOriginalPlaintext() {
    String original = "ghp_supersecrettoken1234567890";
    String encrypted = AesGcmCipher.encrypt(original, KEY_32);
    assertEquals(original, AesGcmCipher.decrypt(encrypted, KEY_32));
  }

  @Test
  void roundTripWorksForEmptyString() {
    String original = "";
    assertEquals(original, AesGcmCipher.decrypt(AesGcmCipher.encrypt(original, KEY_32), KEY_32));
  }

  @Test
  void samePlaintextProducesDifferentCiphertexts() {
    // Each call mints a fresh random IV, so outputs must differ.
    String plain = "token";
    String c1 = AesGcmCipher.encrypt(plain, KEY_32);
    String c2 = AesGcmCipher.encrypt(plain, KEY_32);
    assertNotEquals(c1, c2, "Two encryptions of the same plaintext must produce different outputs (different IVs)");
  }

  @Test
  void tamperedCiphertextThrowsIllegalArgument() {
    String encrypted = AesGcmCipher.encrypt("secret", KEY_32);
    byte[] raw = Base64.getDecoder().decode(encrypted);
    // Flip one bit in the ciphertext portion (beyond the 12-byte IV).
    raw[raw.length - 1] ^= 0xFF;
    String tampered = Base64.getEncoder().encodeToString(raw);

    assertThrows(IllegalArgumentException.class, () -> AesGcmCipher.decrypt(tampered, KEY_32));
  }

  @Test
  void wrongKeyThrowsIllegalArgument() {
    String encrypted = AesGcmCipher.encrypt("secret", KEY_32);
    byte[] wrongKey = new byte[32];
    new SecureRandom().nextBytes(wrongKey);

    assertThrows(IllegalArgumentException.class, () -> AesGcmCipher.decrypt(encrypted, wrongKey));
  }

  @Test
  void tooShortInputThrowsIllegalArgument() {
    // Fewer bytes than the IV alone cannot be valid.
    String tooShort = Base64.getEncoder().encodeToString(new byte[4]);
    assertThrows(IllegalArgumentException.class, () -> AesGcmCipher.decrypt(tooShort, KEY_32));
  }
}
