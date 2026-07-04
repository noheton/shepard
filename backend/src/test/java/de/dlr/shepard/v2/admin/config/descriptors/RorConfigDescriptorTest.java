package de.dlr.shepard.v2.admin.config.descriptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.ror.entities.InstanceRorConfig;
import de.dlr.shepard.v2.admin.ror.io.InstanceRorConfigIO;
import de.dlr.shepard.v2.admin.ror.services.InstanceRorConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** V2CONV-A4 — unit tests for {@link RorConfigDescriptor}. */
class RorConfigDescriptorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private InstanceRorConfigService service;
  private RorConfigDescriptor descriptor;

  private static InstanceRorConfig cfg(String rorId, String org) {
    InstanceRorConfig c = new InstanceRorConfig();
    c.setRorId(rorId);
    c.setOrganizationName(org);
    return c;
  }

  @BeforeEach
  void setUp() {
    service = Mockito.mock(InstanceRorConfigService.class);
    descriptor = new RorConfigDescriptor();
    descriptor.service = service;
  }

  @Test
  void featureNameIsRor() {
    assertEquals("ror", descriptor.featureName());
  }

  @Test
  void currentShapeComputesRorUrl() {
    Mockito.when(service.current()).thenReturn(cfg("04cvxnb49", "DLR"));
    InstanceRorConfigIO io = descriptor.currentShape();
    assertEquals("04cvxnb49", io.rorId());
    assertEquals("https://ror.org/04cvxnb49", io.rorUrl());
  }

  @Test
  void absentFieldLeavesCurrentValue() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg("04cvxnb49", "DLR"));
    Mockito.when(service.patch(Mockito.any(), Mockito.any())).thenAnswer(i -> cfg(i.getArgument(0), i.getArgument(1)));

    // Only organizationName is in the patch; rorId must be carried through.
    descriptor.applyMergePatch(MAPPER.readTree("{\"organizationName\":\"New Name\"}"));

    ArgumentCaptor<String> rorId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> org = ArgumentCaptor.forClass(String.class);
    Mockito.verify(service).patch(rorId.capture(), org.capture());
    assertEquals("04cvxnb49", rorId.getValue());
    assertEquals("New Name", org.getValue());
  }

  @Test
  void explicitNullClearsField() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg("04cvxnb49", "DLR"));
    Mockito.when(service.patch(Mockito.any(), Mockito.any())).thenAnswer(i -> cfg(i.getArgument(0), i.getArgument(1)));

    descriptor.applyMergePatch(MAPPER.readTree("{\"rorId\":null}"));

    ArgumentCaptor<String> rorId = ArgumentCaptor.forClass(String.class);
    Mockito.verify(service).patch(rorId.capture(), Mockito.any());
    assertNull(rorId.getValue());
  }

  @Test
  void invalidRorIdThrows() {
    Mockito.when(service.current()).thenReturn(cfg(null, null));
    ConfigPatchException ex = assertThrows(
      ConfigPatchException.class,
      () -> descriptor.applyMergePatch(MAPPER.readTree("{\"rorId\":\"way-too-long-and-bad!\"}"))
    );
    assertEquals(RorConfigDescriptor.PROBLEM_TYPE_INVALID_ROR_ID, ex.getProblemType());
    Mockito.verify(service, Mockito.never()).patch(Mockito.any(), Mockito.any());
  }

  @Test
  void emptyPatchIsNoOp() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg("04cvxnb49", "DLR"));
    Mockito.when(service.patch(Mockito.any(), Mockito.any())).thenAnswer(i -> cfg(i.getArgument(0), i.getArgument(1)));
    descriptor.applyMergePatch(MAPPER.readTree("{}"));
    Mockito.verify(service).patch("04cvxnb49", "DLR");
  }
}
