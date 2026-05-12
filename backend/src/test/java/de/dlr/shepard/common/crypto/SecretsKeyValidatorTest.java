package de.dlr.shepard.common.crypto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SecretsKeyValidatorTest {

  private SecretsKeyValidator validatorWith(Optional<String> key) {
    SecretsKeyValidator v = new SecretsKeyValidator();
    v.encryptionKey = key;
    return v;
  }

  @Test
  void absentKeyLogsWarningWithoutThrowing() {
    assertDoesNotThrow(() -> validatorWith(Optional.empty()).onStart(null));
  }

  @Test
  void blankKeyLogsWarningWithoutThrowing() {
    assertDoesNotThrow(() -> validatorWith(Optional.of("   ")).onStart(null));
  }

  @Test
  void valid32ByteKeyPasses() {
    String key = Base64.getEncoder().encodeToString(new byte[32]);
    assertDoesNotThrow(() -> validatorWith(Optional.of(key)).onStart(null));
  }

  @Test
  void invalidBase64ThrowsIllegalState() {
    assertThrows(IllegalStateException.class,
      () -> validatorWith(Optional.of("not-base64!!!")).onStart(null));
  }

  @Test
  void wrongLengthKeyThrowsIllegalState() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
    assertThrows(IllegalStateException.class,
      () -> validatorWith(Optional.of(shortKey)).onStart(null));
  }
}
