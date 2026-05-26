package de.dlr.shepard.plugins.aas.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.aas.daos.AasConfigDAO;
import de.dlr.shepard.plugins.aas.entities.AasConfig;
import de.dlr.shepard.plugins.aas.services.AasConfigService.AasPatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * AAS1l — unit tests for {@link AasConfigService}.
 *
 * <p>Pure-unit: Mockito mocks for the DAO; no Quarkus CDI or Neo4j.
 * Pattern follows {@code UnhideConfigServiceTest}.
 */
class AasConfigServiceTest {

  private AasConfigDAO dao;
  private AasConfigService service;

  @BeforeEach
  void setUp() {
    dao = mock(AasConfigDAO.class);
    service = new AasConfigService();
    service.dao = dao;
    // inject install-time defaults via field assignment
    service.installDefaultEnabled = false;
    // Optional fields tested separately
  }

  // ─── seedIfNeeded ────────────────────────────────────────────────

  @Test
  void seedIfNeeded_createsNodeWhenNoneExists() {
    when(dao.findSingleton()).thenReturn(null);
    AasConfig seeded = new AasConfig(1L);
    seeded.setAppId("00000000-0000-7000-0000-000000000001");
    when(dao.createOrUpdate(any(AasConfig.class))).thenReturn(seeded);

    AasConfig result = service.seedIfNeeded();

    assertNotNull(result);
    assertEquals("00000000-0000-7000-0000-000000000001", result.getAppId());
    verify(dao).createOrUpdate(any(AasConfig.class));
  }

  @Test
  void seedIfNeeded_returnsExistingNodeWithoutWriting() {
    AasConfig existing = new AasConfig(1L);
    existing.setAppId("existing-appid");
    when(dao.findSingleton()).thenReturn(existing);

    AasConfig result = service.seedIfNeeded();

    assertEquals("existing-appid", result.getAppId());
    verify(dao, never()).createOrUpdate(any(AasConfig.class));
  }

  // ─── current ─────────────────────────────────────────────────────

  @Test
  void current_returnsSingletonWhenPresent() {
    AasConfig existing = new AasConfig(1L);
    existing.setEnabled(true);
    when(dao.findSingleton()).thenReturn(existing);

    AasConfig result = service.current();

    assertTrue(result.isEnabled());
  }

  @Test
  void current_seedsWhenAbsent() {
    when(dao.findSingleton()).thenReturn(null);
    AasConfig seeded = new AasConfig(1L);
    when(dao.createOrUpdate(any())).thenReturn(seeded);

    AasConfig result = service.current();

    assertNotNull(result);
    verify(dao).createOrUpdate(any(AasConfig.class));
  }

  // ─── patch ───────────────────────────────────────────────────────

  @Test
  void patch_updatesEnabledFlag() {
    AasConfig cfg = new AasConfig(1L);
    cfg.setEnabled(false);
    when(dao.findSingleton()).thenReturn(cfg);
    when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    AasPatch p = new AasPatch();
    p.enabled = Boolean.TRUE;

    AasConfig result = service.patch(p);

    assertTrue(result.isEnabled());
  }

  @Test
  void patch_updatesRegistryUrl() {
    AasConfig cfg = new AasConfig(1L);
    when(dao.findSingleton()).thenReturn(cfg);
    when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    AasPatch p = new AasPatch();
    p.registryUrl = "https://registry.example.dlr.de";
    p.registryUrlTouched = true;

    AasConfig result = service.patch(p);

    assertEquals("https://registry.example.dlr.de", result.getRegistryUrl());
  }

  @Test
  void patch_clearsRegistryUrl_whenExplicitNull() {
    AasConfig cfg = new AasConfig(1L);
    cfg.setRegistryUrl("https://old.example.dlr.de");
    when(dao.findSingleton()).thenReturn(cfg);
    when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    AasPatch p = new AasPatch();
    p.registryUrl = null; // explicit null = clear
    p.registryUrlTouched = true;

    AasConfig result = service.patch(p);

    assertNull(result.getRegistryUrl(), "null + touched should clear the field");
  }

  @Test
  void patch_leavesRegistryUrl_whenAbsent() {
    AasConfig cfg = new AasConfig(1L);
    cfg.setRegistryUrl("https://keep.example.dlr.de");
    when(dao.findSingleton()).thenReturn(cfg);
    when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    AasPatch p = new AasPatch();
    // registryUrlTouched is false (default) — leave alone

    AasConfig result = service.patch(p);

    assertEquals("https://keep.example.dlr.de", result.getRegistryUrl(), "absent field must not be cleared");
  }

  @Test
  void patch_updatesApiKey() {
    AasConfig cfg = new AasConfig(1L);
    when(dao.findSingleton()).thenReturn(cfg);
    when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    AasPatch p = new AasPatch();
    p.registryApiKey = "new-token";
    p.registryApiKeyTouched = true;

    AasConfig result = service.patch(p);

    assertEquals("new-token", result.getRegistryApiKey());
  }

  @Test
  void patch_revokesApiKey_whenExplicitNull() {
    AasConfig cfg = new AasConfig(1L);
    cfg.setRegistryApiKey("existing-token");
    when(dao.findSingleton()).thenReturn(cfg);
    when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    AasPatch p = new AasPatch();
    p.registryApiKey = null;
    p.registryApiKeyTouched = true;

    AasConfig result = service.patch(p);

    assertNull(result.getRegistryApiKey(), "null + touched should revoke the API key");
  }

  @Test
  void patch_doesNotModifyUnchangedFields() {
    AasConfig cfg = new AasConfig(1L);
    cfg.setEnabled(true);
    cfg.setRegistryUrl("https://registry.example.dlr.de");
    cfg.setRegistryApiKey("secret");
    cfg.setBaseUrl("https://shepard.example.dlr.de");
    when(dao.findSingleton()).thenReturn(cfg);
    when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    // Only flip enabled — everything else absent
    AasPatch p = new AasPatch();
    p.enabled = Boolean.FALSE;

    AasConfig result = service.patch(p);

    assertFalse(result.isEnabled());
    assertEquals("https://registry.example.dlr.de", result.getRegistryUrl(), "untouched field preserved");
    assertEquals("secret", result.getRegistryApiKey(), "untouched apiKey preserved");
    assertEquals("https://shepard.example.dlr.de", result.getBaseUrl(), "untouched baseUrl preserved");
  }
}
