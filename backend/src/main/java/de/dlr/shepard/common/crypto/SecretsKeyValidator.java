package de.dlr.shepard.common.crypto;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.Base64;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Validates {@code shepard.secrets.encryption-key} at startup.
 *
 * <p>The key must be base64-encoded 32 bytes (256 bits) for AES-256-GCM.
 * If the property is absent or empty, PAT storage is disabled — the
 * credential PATCH endpoint returns 501 Not Implemented. This is
 * intentional: operators who don't need encrypted PAT storage don't
 * have to configure the key.
 *
 * <p>If the property IS set but base64-decodes to something other than
 * 32 bytes, that is a configuration mistake (the admin clearly intended
 * to enable encryption but supplied a bad key), so we abort startup
 * with a clear error rather than silently disabling the feature.
 */
@ApplicationScoped
public class SecretsKeyValidator {

  @ConfigProperty(name = "shepard.secrets.encryption-key")
  Optional<String> encryptionKey;

  void onStart(@Observes StartupEvent ev) {
    if (encryptionKey.isEmpty() || encryptionKey.get().isBlank()) {
      Log.warn(
        "shepard.secrets.encryption-key is not configured — PAT storage is disabled. " +
        "PATCH /v2/me/git-credentials will return 501 Not Implemented until a 32-byte base64 key is set."
      );
      return;
    }

    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(encryptionKey.get().trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
        "shepard.secrets.encryption-key must be a base64-encoded 32-byte (256-bit) key — " +
        "the value is not valid base64",
        e
      );
    }

    if (decoded.length != 32) {
      throw new IllegalStateException(
        "shepard.secrets.encryption-key must be a base64-encoded 32-byte (256-bit) key — " +
        "decoded length is " + decoded.length + " bytes (expected 32)"
      );
    }
  }
}
