package de.dlr.shepard.plugins.minter.datacite.entities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * KIP1d — smoke tests for the {@code :DataciteMinterConfig} entity
 * shape. Focused on default values + identity (equals/hashCode by
 * appId) — fields are Lombok-generated so we trust those.
 */
class DataciteMinterConfigTest {

  @Test
  void defaults_areSafeForFreshInstall() {
    DataciteMinterConfig cfg = new DataciteMinterConfig();

    assertThat(cfg.isEnabled()).as("default enabled=false — operators opt in").isFalse();
    assertThat(cfg.getApiBaseUrl()).isEqualTo("https://api.test.datacite.org");
    assertThat(cfg.getDefaultState()).isEqualTo(DataciteMinterConfig.STATE_DRAFT);
    assertThat(cfg.getHandlePrefix()).isNull();
    assertThat(cfg.getRepositoryId()).isNull();
    assertThat(cfg.getPasswordCipher()).isNull();
    assertThat(cfg.getPasswordHash()).isNull();
  }

  @Test
  void stateConstants_areTheCanonicalThreeValues() {
    assertThat(DataciteMinterConfig.STATE_DRAFT).isEqualTo("draft");
    assertThat(DataciteMinterConfig.STATE_REGISTERED).isEqualTo("registered");
    assertThat(DataciteMinterConfig.STATE_FINDABLE).isEqualTo("findable");
  }

  @Test
  void identity_isByAppId() {
    DataciteMinterConfig a = new DataciteMinterConfig();
    a.setAppId("the-appid");

    DataciteMinterConfig b = new DataciteMinterConfig();
    b.setAppId("the-appid");

    DataciteMinterConfig c = new DataciteMinterConfig();
    c.setAppId("other");

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a).isNotEqualTo(c);
  }

  @Test
  void equals_handlesNullAndOtherTypes() {
    DataciteMinterConfig a = new DataciteMinterConfig();
    a.setAppId("x");
    assertThat(a.equals(null)).isFalse();
    assertThat(a.equals("not a config")).isFalse();
    assertThat(a.equals(a)).isTrue();
  }

  @Test
  void testOnlyConstructor_setsId() {
    DataciteMinterConfig c = new DataciteMinterConfig(42L);
    assertThat(c.getId()).isEqualTo(42L);
  }
}
