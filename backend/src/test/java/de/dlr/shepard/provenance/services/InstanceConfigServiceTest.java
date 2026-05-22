package de.dlr.shepard.provenance.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.provenance.daos.InstanceConfigDAO;
import de.dlr.shepard.provenance.entities.InstanceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class InstanceConfigServiceTest {

  @Mock
  InstanceConfigDAO dao;

  InstanceConfigService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new InstanceConfigService();
    service.dao = dao;
    service.configuredSecret = "";
    when(dao.createOrUpdate(any(InstanceConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void currentSeedsSecretFromSecureRandomWhenEnvAbsent() {
    when(dao.findSingleton()).thenReturn(null);

    InstanceConfig cfg = service.current();

    assertThat(cfg.getInstanceSecret()).isNotBlank();
    assertThat(cfg.getSecretVersion()).isEqualTo(1);
    assertThat(cfg.getCreatedAtMillis()).isNotNull();
  }

  @Test
  void currentSeedsFromConfiguredSecretWhenPresent() {
    service.configuredSecret = "operator-supplied-secret";
    when(dao.findSingleton()).thenReturn(null);

    InstanceConfig cfg = service.current();

    assertThat(cfg.getInstanceSecret()).isEqualTo("operator-supplied-secret");
    assertThat(cfg.getSecretVersion()).isEqualTo(1);
  }

  @Test
  void currentReturnsExistingSingletonUnchanged() {
    InstanceConfig existing = new InstanceConfig();
    existing.setInstanceSecret("preserved");
    existing.setSecretVersion(5);
    when(dao.findSingleton()).thenReturn(existing);

    InstanceConfig cfg = service.current();

    assertThat(cfg.getInstanceSecret()).isEqualTo("preserved");
    assertThat(cfg.getSecretVersion()).isEqualTo(5);
    verify(dao, times(0)).createOrUpdate(any());
  }

  @Test
  void rotateIncrementsVersionAndChangesSecret() {
    InstanceConfig existing = new InstanceConfig();
    existing.setInstanceSecret("old-value");
    existing.setSecretVersion(1);
    existing.setCreatedAtMillis(1000L);
    when(dao.findSingleton()).thenReturn(existing);

    InstanceConfig rotated = service.rotate();

    assertThat(rotated.getSecretVersion()).isEqualTo(2);
    assertThat(rotated.getInstanceSecret()).isNotEqualTo("old-value");
    assertThat(rotated.getLastRotatedAtMillis()).isNotNull();
  }
}
