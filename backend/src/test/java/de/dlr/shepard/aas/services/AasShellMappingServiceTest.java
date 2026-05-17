package de.dlr.shepard.aas.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.v2.aas.io.AasReferenceIO;
import de.dlr.shepard.v2.aas.io.AasShellIO;
import java.util.List;
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
  void submodelsEmptyWhenNoDataObjects() {
    Collection c = buildCollection("x", "Name", null);
    assertTrue(service.toShell(c).getSubmodels().isEmpty());
  }

  @Test
  void toShellWithDataObjectsPopulatesSubmodels() {
    Collection c = buildCollection("col-aaa", "Alpha", null);
    DataObject d1 = mock(DataObject.class);
    DataObject d2 = mock(DataObject.class);
    when(d1.getAppId()).thenReturn("do-111");
    when(d2.getAppId()).thenReturn("do-222");

    AasShellIO shell = service.toShell(c, List.of(d1, d2));

    assertEquals(2, shell.getSubmodels().size());
    AasReferenceIO ref1 = shell.getSubmodels().get(0);
    assertEquals("ExternalReference", ref1.type());
    assertEquals(1, ref1.keys().size());
    assertEquals("Submodel", ref1.keys().get(0).type());
    assertEquals("urn:shepard:dataobject:do-111", ref1.keys().get(0).value());
  }

  @Test
  void toSubmodelRefsBuildsCorrectShape() {
    DataObject d = mock(DataObject.class);
    when(d.getAppId()).thenReturn("do-abc");

    List<AasReferenceIO> refs = service.toSubmodelRefs(List.of(d));

    assertEquals(1, refs.size());
    assertEquals("ExternalReference", refs.get(0).type());
    assertEquals("Submodel", refs.get(0).keys().get(0).type());
    assertEquals("urn:shepard:dataobject:do-abc", refs.get(0).keys().get(0).value());
  }

  @Test
  void toSubmodelRefsEmptyForNoDataObjects() {
    assertTrue(service.toSubmodelRefs(List.of()).isEmpty());
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
