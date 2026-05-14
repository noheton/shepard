package de.dlr.shepard.plugins.references.dbpediadatabus.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.plugins.references.dbpediadatabus.entities.DbpediaDatabusConfig;
import java.util.Date;
import java.util.List;

/**
 * REF1c — JSON shape returned by
 * {@code GET /v2/admin/references/dbpedia-databus/config}.
 *
 * <p>The raw {@code oauthClientSecretCipher} is <b>never</b>
 * serialised — instead the boolean {@code oauthClientSecretSet} +
 * the masked {@code oauthClientSecretFingerprint} surface so an
 * operator can confirm a secret is stored without exposing material
 * that reverses to plaintext.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DbpediaDatabusConfigIO(
  boolean enabled,
  String defaultEndpoint,
  List<String> allowedHosts,
  long cacheTtlSeconds,
  String authMode,
  String oauthTokenUrl,
  String oauthClientId,
  boolean oauthClientSecretSet,
  String oauthClientSecretFingerprint,
  Date updatedAt,
  String updatedBy
) {
  public static DbpediaDatabusConfigIO from(DbpediaDatabusConfig cfg) {
    Long updatedAt = cfg.getUpdatedAtMillis();
    return new DbpediaDatabusConfigIO(
      cfg.isEnabled(),
      cfg.getDefaultEndpoint(),
      cfg.getAllowedHosts(),
      cfg.getCacheTtlSeconds(),
      cfg.getAuthMode(),
      cfg.getOauthTokenUrl(),
      cfg.getOauthClientId(),
      cfg.isOauthClientSecretSet(),
      cfg.getOauthClientSecretFingerprint(),
      updatedAt == null ? null : new Date(updatedAt),
      cfg.getUpdatedBy()
    );
  }
}
