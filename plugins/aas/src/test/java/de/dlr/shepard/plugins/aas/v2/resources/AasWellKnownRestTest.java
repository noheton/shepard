package de.dlr.shepard.plugins.aas.v2.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.aas.services.AasServerSelfDescriptionService;
import de.dlr.shepard.plugins.aas.v2.io.AasServerSelfDescriptionIO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AasWellKnownRestTest {

  @Mock
  AasServerSelfDescriptionService service;

  AasWellKnownRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new AasWellKnownRest();
    resource.service = service;
  }

  @Test
  void describeDelegatesToServiceAndReturns200() {
    var io = new AasServerSelfDescriptionIO(
      false,
      "Submodel-Repository-Read-3.1",
      Map.of(),
      List.of(),
      0L,
      List.of()
    );
    when(service.describe()).thenReturn(io);
    var r = resource.describe();
    assertEquals(200, r.getStatus());
    assertSame(io, r.getEntity());
  }
}
