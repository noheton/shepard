package de.dlr.shepard.plugins.minter.epic.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.plugins.minter.epic.entities.EpicMinterConfig;
import de.dlr.shepard.plugins.minter.epic.services.EpicMinterConfigService;
import java.util.Date;

/**
 * KIP1c — JSON shape returned by
 * {@code GET /v2/admin/minters/epic/config}.
 *
 * <p>Notably the {@code credentialKey} and {@code credentialHash}
 * fields on the singleton are <b>never</b> serialised through this
 * IO — instead the masked fingerprint (first 8 hex chars of the
 * SHA-256) + {@code credentialSet} boolean are surfaced. An operator
 * can use the fingerprint to confirm "yes that's the credential I
 * just set" without exposing material that could help an attacker.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EpicMinterConfigIO(
  boolean enabled,
  String apiBaseUrl,
  String handlePrefix,
  boolean credentialSet,
  String credentialFingerprint,
  Date updatedAt,
  String updatedBy
) {
  /**
   * Project a {@link EpicMinterConfig} entity onto the IO,
   * replacing the credential material with the fingerprint shape.
   */
  public static EpicMinterConfigIO from(EpicMinterConfig cfg) {
    Long updatedAtMillis = cfg.getUpdatedAt();
    return new EpicMinterConfigIO(
      cfg.isEnabled(),
      cfg.getApiBaseUrl(),
      cfg.getHandlePrefix(),
      cfg.getCredentialHash() != null && !cfg.getCredentialHash().isBlank(),
      EpicMinterConfigService.fingerprint(cfg.getCredentialHash()),
      updatedAtMillis == null ? null : new Date(updatedAtMillis),
      cfg.getUpdatedBy()
    );
  }
}
