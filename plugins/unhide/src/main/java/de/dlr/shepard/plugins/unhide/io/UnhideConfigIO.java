package de.dlr.shepard.plugins.unhide.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.plugins.unhide.entities.UnhideConfig;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService;
import java.util.Date;

/**
 * UH1a — JSON shape returned by {@code GET /v2/admin/unhide/config}.
 *
 * <p>Notably the {@code harvestApiKeyHash} field on the singleton is
 * <b>never</b> serialised through this IO — instead the masked
 * fingerprint (first 8 hex chars of the SHA-256) + the
 * mint timestamp are surfaced. An operator can use the fingerprint
 * to confirm "yes this is the key I just minted" without exposing
 * material that would let an attacker reverse the plaintext from a
 * leaked admin-API response.
 *
 * <p>{@code @JsonInclude(NON_NULL)} so optional fields
 * ({@code contactEmail}, {@code harvestApiKeyMintedAt},
 * {@code harvestApiKeyFingerprint}) are dropped when unset.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnhideConfigIO(
  boolean enabled,
  boolean feedPublic,
  String contactEmail,
  Date harvestApiKeyMintedAt,
  String harvestApiKeyFingerprint
) {
  /**
   * Project an {@link UnhideConfig} entity onto the IO, replacing
   * the raw hash with the fingerprint + rotated-at timestamp.
   */
  public static UnhideConfigIO from(UnhideConfig cfg) {
    Long rotatedAt = cfg.getHarvestApiKeyLastRotatedAt();
    return new UnhideConfigIO(
      cfg.isEnabled(),
      cfg.isFeedPublic(),
      cfg.getContactEmail(),
      rotatedAt == null ? null : new Date(rotatedAt),
      UnhideConfigService.fingerprint(cfg.getHarvestApiKeyHash())
    );
  }
}
