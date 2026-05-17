package de.dlr.shepard.aas.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.v2.aas.io.AasShellIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests for AAS1a: {@link AasShellMappingService}. */
class AasShellMappingServiceTest {

  AasShellMappingService service;

  @BeforeEach
  void setUp() {
    service = new AasShellMappingService();
  }

  private Collection buildCollection(String appId, String name, String description) {
    Collection c = new Collection(1L);
    c.setAppId(appId);
    c.setName(name);
    c.setDescription(description);
    return c;
  }

  @Test
  void idIsUrnFromAppId() {
    Collection c = buildCollection("abc-123", "My Collection", null);
    AasShellIO shell = service.toShell(c);
    assertEquals("urn:shepard:collection:abc-123", shell.getId());
  }

  @Test
  void globalAssetIdIsUrnFromAppId() {
    Collection c = buildCollection("abc-123", "My Collection", null);
    AasShellIO shell = service.toShell(c);
    assertEquals("urn:shepard:asset:abc-123", shell.getAssetInformation().getGlobalAssetId());
  }

  @Test
  void assetKindIsInstance() {
    Collection c = buildCollection("x", "Name", null);
    assertEquals("Instance", service.toShell(c).getAssetInformation().getAssetKind());
  }

  @Test
  void descriptionAbsentWhenCollectionHasNone() {
    Collection c = buildCollection("x", "Name", null);
    assertNull(service.toShell(c).getDescription());
  }

  @Test
  void descriptionPresentWithEnglishLangTag() {
    Collection c = buildCollection("x", "Name", "A test collection.");
    var desc = service.toShell(c).getDescription();
    assertEquals(1, desc.size());
    assertEquals("en", desc.get(0).getLanguage());
    assertEquals("A test collection.", desc.get(0).getText());
  }

  @Test
  void submodelsAlwaysEmpty() {
    Collection c = buildCollection("x", "Name", null);
    assertTrue(service.toShell(c).getSubmodels().isEmpty());
  }

  @ParameterizedTest
  @CsvSource({
    "My Collection,   My_Collection",
    "123abc,          c_123abc",
    "hello world!,    hello_world_",
    ",                c_unnamed",
    "A,               A"
  })
  void sanitiseIdShort(String input, String expected) {
    assertEquals(expected.strip(), AasShellMappingService.sanitiseIdShort(
        input == null || input.isBlank() ? null : input.strip()));
  }
}
