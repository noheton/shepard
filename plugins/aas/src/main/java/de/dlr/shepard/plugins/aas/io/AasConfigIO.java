package de.dlr.shepard.plugins.aas.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.plugins.aas.entities.AasConfig;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * AAS1l — JSON shape returned by {@code GET /v2/admin/aas/config}.
 *
 * <p>The {@code registryApiKey} field on the singleton is
 * <b>never</b> serialised through this IO — instead a boolean
 * {@code apiKeyPresent} flag surfaces whether a key is stored.
 * This prevents a leaked admin-API response from exposing an
 * outbound registry credential.
 *
 * <p>{@code @JsonInclude(NON_NULL)} so optional fields
 * ({@code registryUrl}, {@code baseUrl}) are dropped when unset.
 */
@Schema(name = "AasConfigIO", description = "AAS plugin runtime config returned by GET /v2/admin/aas/config.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AasConfigIO(
  boolean enabled,
  String registryUrl,
  boolean apiKeyPresent,
  String baseUrl
) {
  /**
   * Project an {@link AasConfig} entity onto the IO, replacing the
   * raw {@code registryApiKey} with a boolean presence flag.
   */
  public static AasConfigIO from(AasConfig cfg) {
    return new AasConfigIO(
      cfg.isEnabled(),
      cfg.getRegistryUrl(),
      cfg.getRegistryApiKey() != null && !cfg.getRegistryApiKey().isBlank(),
      cfg.getBaseUrl()
    );
  }
}
