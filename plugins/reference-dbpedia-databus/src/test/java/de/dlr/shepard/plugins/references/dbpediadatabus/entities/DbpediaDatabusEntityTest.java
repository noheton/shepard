package de.dlr.shepard.plugins.references.dbpediadatabus.entities;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusConfigIO;
import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusReferenceIO;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * REF1c — smoke tests for entity + IO classes (no Quarkus/Neo4j).
 */
class DbpediaDatabusEntityTest {

  @Test
  void dbpediaDatabusConfig_defaultValues() {
    DbpediaDatabusConfig cfg = new DbpediaDatabusConfig();
    assertThat(cfg.isEnabled()).isFalse();
    assertThat(cfg.getDefaultEndpoint()).isEqualTo(DbpediaDatabusConfig.DEFAULT_ENDPOINT);
    assertThat(cfg.getCacheTtlSeconds()).isEqualTo(DbpediaDatabusConfig.DEFAULT_CACHE_TTL_SECONDS);
    assertThat(cfg.getAuthMode()).isEqualTo(DbpediaDatabusConfig.AUTH_MODE_NONE);
    assertThat(cfg.isOauthClientSecretSet()).isFalse();
  }

  @Test
  void dbpediaDatabusConfig_equalsOnAppId() {
    DbpediaDatabusConfig a = new DbpediaDatabusConfig();
    a.setAppId("app01");
    DbpediaDatabusConfig b = new DbpediaDatabusConfig();
    b.setAppId("app01");
    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  void dbpediaDatabusConfig_notEqualsOnDifferentAppId() {
    DbpediaDatabusConfig a = new DbpediaDatabusConfig();
    a.setAppId("app01");
    DbpediaDatabusConfig b = new DbpediaDatabusConfig();
    b.setAppId("app02");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void dbpediaDatabusReference_constructorSetsArtifactUri() {
    DbpediaDatabusReference ref = new DbpediaDatabusReference("https://databus.dbpedia.org/art");
    assertThat(ref.getArtifactUri()).isEqualTo("https://databus.dbpedia.org/art");
  }

  @Test
  void dbpediaDatabusReference_statusConstants() {
    assertThat(DbpediaDatabusReference.STATUS_FRESH).isEqualTo("fresh");
    assertThat(DbpediaDatabusReference.STATUS_STALE).isEqualTo("stale");
    assertThat(DbpediaDatabusReference.STATUS_UNAVAILABLE).isEqualTo("unavailable");
  }

  // ─── ConfigIO ────────────────────────────────────────────────────────────

  @Test
  void configIO_from_mapsAllFields() {
    DbpediaDatabusConfig cfg = new DbpediaDatabusConfig();
    cfg.setEnabled(true);
    cfg.setDefaultEndpoint("https://databus.dbpedia.org");
    cfg.setAllowedHosts(List.of("databus.dbpedia.org"));
    cfg.setCacheTtlSeconds(86400L);
    cfg.setAuthMode("none");
    cfg.setOauthTokenUrl("https://token.example.org");
    cfg.setOauthClientId("client-id");
    cfg.setOauthClientSecretSet(true);
    cfg.setOauthClientSecretFingerprint("abcd1234");
    cfg.setUpdatedAtMillis(1000000L);
    cfg.setUpdatedBy("admin");

    DbpediaDatabusConfigIO io = DbpediaDatabusConfigIO.from(cfg);

    assertThat(io.enabled()).isTrue();
    assertThat(io.defaultEndpoint()).isEqualTo("https://databus.dbpedia.org");
    assertThat(io.allowedHosts()).containsExactly("databus.dbpedia.org");
    assertThat(io.cacheTtlSeconds()).isEqualTo(86400L);
    assertThat(io.authMode()).isEqualTo("none");
    assertThat(io.oauthTokenUrl()).isEqualTo("https://token.example.org");
    assertThat(io.oauthClientId()).isEqualTo("client-id");
    assertThat(io.oauthClientSecretSet()).isTrue();
    assertThat(io.oauthClientSecretFingerprint()).isEqualTo("abcd1234");
    assertThat(io.updatedAt()).isEqualTo(new Date(1000000L));
    assertThat(io.updatedBy()).isEqualTo("admin");
  }

  @Test
  void configIO_from_nullUpdatedAt_nullInIO() {
    DbpediaDatabusConfig cfg = new DbpediaDatabusConfig();
    cfg.setUpdatedAtMillis(null);

    DbpediaDatabusConfigIO io = DbpediaDatabusConfigIO.from(cfg);
    assertThat(io.updatedAt()).isNull();
  }

  // ─── ReferenceIO ─────────────────────────────────────────────────────────

  @Test
  void referenceIO_from_mapsAllFields() {
    DbpediaDatabusReference ref = new DbpediaDatabusReference("https://databus.dbpedia.org/art");
    ref.setAppId("ref01");
    ref.setCachedTitle("Title");
    ref.setCachedAbstract("Abstract");
    ref.setCachedVersion("2024");
    ref.setCachedLicence("CC0-1.0");
    ref.setCachedModifiedAtMillis(2000000L);
    ref.setCacheFetchedAtMillis(3000000L);
    ref.setCacheStatus("fresh");

    DbpediaDatabusReferenceIO io = new DbpediaDatabusReferenceIO(ref);

    assertThat(io.getAppId()).isEqualTo("ref01");
    assertThat(io.getArtifactUri()).isEqualTo("https://databus.dbpedia.org/art");
    assertThat(io.getCachedTitle()).isEqualTo("Title");
    assertThat(io.getCachedAbstract()).isEqualTo("Abstract");
    assertThat(io.getCachedVersion()).isEqualTo("2024");
    assertThat(io.getCachedLicence()).isEqualTo("CC0-1.0");
    assertThat(io.getCachedModifiedAt()).isEqualTo(new Date(2000000L));
    assertThat(io.getCacheFetchedAt()).isEqualTo(new Date(3000000L));
    assertThat(io.getCacheStatus()).isEqualTo("fresh");
  }
}
