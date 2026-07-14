package de.dlr.shepard.plugins.minter.datacite.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.plugins.minter.datacite.entities.DataciteMinterConfig;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService;
import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * KIP1d — JSON shape returned by
 * {@code GET /v2/admin/minters/datacite/config}.
 *
 * <p>Notably the {@code passwordCipher} and {@code passwordHash}
 * fields on the singleton are <b>never</b> serialised through this
 * IO — instead the masked fingerprint (first 8 hex chars of the
 * SHA-256) + {@code passwordSet} boolean are surfaced. An operator
 * can use the fingerprint to confirm "yes that's the password I
 * just set" without exposing material that could help an attacker.
 */
@Schema(name = "DataciteMinterConfigIO", description = "DataCite minter runtime config returned by GET /v2/admin/minters/datacite/config.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataciteMinterConfigIO(
  boolean enabled,
  String apiBaseUrl,
  String handlePrefix,
  String repositoryId,
  boolean passwordSet,
  String passwordFingerprint,
  String publisher,
  String landingPageBase,
  String defaultState,
  String updatedAt,
  String updatedBy
) {
  /**
   * Project a {@link DataciteMinterConfig} entity onto the IO,
   * replacing the credential material with the fingerprint shape.
   */
  public static DataciteMinterConfigIO from(DataciteMinterConfig cfg) {
    Long updatedAtMillis = cfg.getUpdatedAt();
    return new DataciteMinterConfigIO(
      cfg.isEnabled(),
      cfg.getApiBaseUrl(),
      cfg.getHandlePrefix(),
      cfg.getRepositoryId(),
      cfg.getPasswordHash() != null && !cfg.getPasswordHash().isBlank(),
      DataciteMinterConfigService.fingerprint(cfg.getPasswordHash()),
      cfg.getPublisher(),
      cfg.getLandingPageBase(),
      cfg.getDefaultState(),
      updatedAtMillis == null ? null : Instant.ofEpochMilli(updatedAtMillis).toString(),
      cfg.getUpdatedBy()
    );
  }
}
