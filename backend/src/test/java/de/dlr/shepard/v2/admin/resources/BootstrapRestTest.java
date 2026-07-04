package de.dlr.shepard.v2.admin.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.admin.io.BootstrapRequestIO;
import de.dlr.shepard.v2.admin.io.BootstrapResponseIO;
import de.dlr.shepard.v2.admin.services.BootstrapService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class BootstrapRestTest {

  @Mock
  BootstrapService bootstrapService;

  @InjectMocks
  BootstrapRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void bootstrap_returns201WithTypedBody() {
    BootstrapRequestIO body = new BootstrapRequestIO();
    body.setToken("secret-token");
    body.setUsername("admin-user");
    when(bootstrapService.consumeBootstrap("secret-token", "admin-user")).thenReturn("admin-user");

    Response r = resource.bootstrap(body);

    assertEquals(201, r.getStatus());
    BootstrapResponseIO entity = (BootstrapResponseIO) r.getEntity();
    assertEquals("admin-user", entity.getUsername());
    assertEquals("instance-admin", entity.getRole());
  }
}
