package de.dlr.shepard.plugins.unhide.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.plugins.unhide.entities.UnhideConfig;
import de.dlr.shepard.plugins.unhide.io.UnhideConfigIO;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService.UnhidePatch;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** V2CONV-A7 — unit tests for {@link UnhideConfigDescriptor}. */
class UnhideConfigDescriptorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private UnhideConfigService service;
  private UnhideConfigDescriptor descriptor;

  private static UnhideConfig cfg(boolean enabled, boolean feedPublic, String contactEmail) {
    UnhideConfig c = new UnhideConfig();
    c.setEnabled(enabled);
    c.setFeedPublic(feedPublic);
    c.setContactEmail(contactEmail);
    return c;
  }

  @BeforeEach
  void setUp() {
    service = Mockito.mock(UnhideConfigService.class);
    descriptor = new UnhideConfigDescriptor();
    descriptor.service = service;
  }

  @Test
  void featureNameIsUnhide() {
    assertEquals("unhide", descriptor.featureName());
  }

  @Test
  void descriptionIsNonBlank() {
    String desc = descriptor.description();
    assertNotNull(desc);
    assertTrue(desc.length() > 10);
  }

  @Test
  void currentShape_delegatesToService() {
    UnhideConfig c = cfg(true, false, "ops@example.dlr.de");
    c.setHarvestApiKeyHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    c.setHarvestApiKeyLastRotatedAt(1700000000000L);
    Mockito.when(service.current()).thenReturn(c);

    UnhideConfigIO io = descriptor.currentShape();

    assertTrue(io.enabled());
    assertEquals("ops@example.dlr.de", io.contactEmail());
    assertEquals("01234567", io.harvestApiKeyFingerprint(), "fingerprint = first 8 hex chars");
    assertNotNull(io.harvestApiKeyMintedAt());
  }

  @Test
  void enableFlip() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(false, false, null));
    ArgumentCaptor<UnhidePatch> cap = ArgumentCaptor.forClass(UnhidePatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(true, false, null));

    UnhideConfigIO io = descriptor.applyMergePatch(MAPPER.readTree("{\"enabled\":true}"));

    assertTrue(io.enabled());
    assertEquals(Boolean.TRUE, cap.getValue().enabled);
  }

  @Test
  void explicitNullOnEnabledLeavesAlone() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true, false, null));
    ArgumentCaptor<UnhidePatch> cap = ArgumentCaptor.forClass(UnhidePatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(true, false, null));

    descriptor.applyMergePatch(MAPPER.readTree("{\"enabled\":null}"));

    assertNull(cap.getValue().enabled, "null enabled must leave the field untouched");
  }

  @Test
  void feedPublicFlip() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true, false, null));
    ArgumentCaptor<UnhidePatch> cap = ArgumentCaptor.forClass(UnhidePatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(true, true, null));

    descriptor.applyMergePatch(MAPPER.readTree("{\"feedPublic\":true}"));

    assertEquals(Boolean.TRUE, cap.getValue().feedPublic);
  }

  @Test
  void contactEmailSet() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true, false, null));
    ArgumentCaptor<UnhidePatch> cap = ArgumentCaptor.forClass(UnhidePatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(true, false, "alice@example.dlr.de"));

    descriptor.applyMergePatch(MAPPER.readTree("{\"contactEmail\":\"alice@example.dlr.de\"}"));

    assertTrue(cap.getValue().contactEmailTouched);
    assertEquals("alice@example.dlr.de", cap.getValue().contactEmail);
  }

  @Test
  void contactEmailNullClears() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true, false, "old@example.dlr.de"));
    ArgumentCaptor<UnhidePatch> cap = ArgumentCaptor.forClass(UnhidePatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(true, false, null));

    descriptor.applyMergePatch(MAPPER.readTree("{\"contactEmail\":null}"));

    assertTrue(cap.getValue().contactEmailTouched, "explicit null must set touched=true");
    assertNull(cap.getValue().contactEmail, "explicit null must clear the email");
  }

  @Test
  void absentFieldsAreNotTouched() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true, false, null));
    ArgumentCaptor<UnhidePatch> cap = ArgumentCaptor.forClass(UnhidePatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(true, false, null));

    descriptor.applyMergePatch(MAPPER.readTree("{}"));

    UnhidePatch p = cap.getValue();
    assertNull(p.enabled, "absent enabled → null (leave alone)");
    assertNull(p.feedPublic, "absent feedPublic → null (leave alone)");
    assertTrue(!p.contactEmailTouched, "absent contactEmail → touched=false");
  }

  @Test
  void harvestApiKeyHashPatchThrows() {
    Mockito.when(service.current()).thenReturn(cfg(true, false, null));

    ConfigPatchException ex = assertThrows(
      ConfigPatchException.class,
      () -> descriptor.applyMergePatch(MAPPER.readTree("{\"harvestApiKeyHash\":\"abc\"}"))
    );
    assertEquals(UnhideConfigDescriptor.PROBLEM_TYPE_READ_ONLY_FIELD, ex.getProblemType());
    assertTrue(ex.getDetail().contains("harvestApiKeyHash"));
  }
}
