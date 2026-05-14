package de.dlr.shepard.plugins.references.dbpediadatabus.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * REF1c — runtime-mutable DBpedia-Databus-integration config
 * singleton, mirroring the {@code :UnhideConfig} (UH1a) +
 * {@code :SemanticConfig} (N1c2) shapes per CLAUDE.md "admin-
 * configurable at runtime".
 *
 * <p>The OAuth client secret plaintext never enters this node — only
 * the AES-GCM cipher (per the same pattern as G1-cred PATs). The
 * plaintext travels exactly twice: (1) inbound on
 * {@code POST /v2/admin/references/dbpedia-databus/credential},
 * (2) outbound on each OAuth token-exchange. Never logged (only the
 * fingerprint), never returned in REST.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class DbpediaDatabusConfig implements HasAppId {

  public static final String DEFAULT_ENDPOINT = "https://databus.dbpedia.org";
  public static final long DEFAULT_CACHE_TTL_SECONDS = 86400L;
  public static final String AUTH_MODE_NONE = "none";
  public static final String AUTH_MODE_OAUTH_CC = "oauth-client-credentials";
  public static final int FINGERPRINT_LENGTH = 8;

  @Id
  @GeneratedValue
  private Long id;

  @Property("appId")
  private String appId;

  @Property("enabled")
  private boolean enabled = false;

  @Property("defaultEndpoint")
  private String defaultEndpoint = DEFAULT_ENDPOINT;

  @Property("allowedHosts")
  private List<String> allowedHosts = new ArrayList<>(List.of("databus.dbpedia.org"));

  @Property("cacheTtlSeconds")
  private long cacheTtlSeconds = DEFAULT_CACHE_TTL_SECONDS;

  @Property("authMode")
  private String authMode = AUTH_MODE_NONE;

  @Property("oauthTokenUrl")
  private String oauthTokenUrl;

  @Property("oauthClientId")
  private String oauthClientId;

  /**
   * AES-GCM-encrypted client secret (base64({@code IV ‖ ciphertext +
   * tag})). Never logged, never returned, never decoded outside the
   * credential service.
   */
  @Property("oauthClientSecretCipher")
  private String oauthClientSecretCipher;

  @Property("oauthClientSecretSet")
  private boolean oauthClientSecretSet;

  @Property("oauthClientSecretFingerprint")
  private String oauthClientSecretFingerprint;

  @Property("updatedAtMillis")
  private Long updatedAtMillis;

  @Property("updatedBy")
  private String updatedBy;

  /** For testing purposes only. */
  public DbpediaDatabusConfig(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof DbpediaDatabusConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
