package de.dlr.shepard.data.hdf.hsds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HsdsClientTest {

  private HttpServer server;
  private List<String> capturedAuthHeaders;
  private List<String> capturedMethods;
  private List<String> capturedUris;
  private AtomicInteger nextStatus;
  private String nextBody;

  @BeforeEach
  void start() throws IOException {
    capturedAuthHeaders = new CopyOnWriteArrayList<>();
    capturedMethods = new CopyOnWriteArrayList<>();
    capturedUris = new CopyOnWriteArrayList<>();
    nextStatus = new AtomicInteger(201);
    nextBody = "{}";
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      capturedAuthHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
      capturedMethods.add(exchange.getRequestMethod());
      capturedUris.add(exchange.getRequestURI().toString());
      // Drain the body even on empty PUT to keep the keep-alive socket clean.
      try (var is = exchange.getRequestBody()) {
        is.readAllBytes();
      }
      byte[] body = nextBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(nextStatus.get(), body.length);
      try (var os = exchange.getResponseBody()) {
        os.write(body);
      }
    });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private HsdsClient clientFor(String user, String pass) {
    String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
    return new HsdsClient(endpoint, user, pass, Duration.ofSeconds(5), HttpClient.newHttpClient());
  }

  @Test
  void createDomainSuccessSendsPutWithBasicAuth() {
    nextStatus.set(201);
    var client = clientFor("admin", "secret");
    client.createDomain("/shepard/abc/");
    assertEquals(1, capturedMethods.size());
    assertEquals("PUT", capturedMethods.get(0));
    assertTrue(capturedUris.get(0).contains("domain=/shepard/abc/"));
    String expectedAuth = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes(StandardCharsets.UTF_8));
    assertEquals(expectedAuth, capturedAuthHeaders.get(0));
  }

  @Test
  void createDomainAccepts200() {
    nextStatus.set(200);
    var client = clientFor("admin", "secret");
    client.createDomain("/shepard/foo/");
    assertEquals("PUT", capturedMethods.get(0));
  }

  @Test
  void deleteDomainSuccessSendsDelete() {
    nextStatus.set(200);
    var client = clientFor("admin", "secret");
    client.deleteDomain("/shepard/abc/");
    assertEquals(1, capturedMethods.size());
    assertEquals("DELETE", capturedMethods.get(0));
  }

  @Test
  void unauthorizedRaisesOperatorReadableMessage() {
    nextStatus.set(401);
    nextBody = "{\"error\":\"unauthorized\"}";
    var client = clientFor("admin", "wrong");
    var ex = assertThrows(HsdsClient.HsdsException.class, () -> client.createDomain("/shepard/abc/"));
    assertTrue(
      ex.getMessage().contains("Verify shepard.hdf.hsds.username"),
      "operator-readable hint missing: " + ex.getMessage()
    );
  }

  @Test
  void forbiddenSurfacesAsHsdsException() {
    nextStatus.set(403);
    var client = clientFor("admin", "wrong");
    var ex = assertThrows(HsdsClient.HsdsException.class, () -> client.deleteDomain("/shepard/abc/"));
    assertTrue(ex.getMessage().contains("HTTP 403"));
  }

  @Test
  void serverErrorIncludesBodySnippet() {
    nextStatus.set(500);
    nextBody = "internal boom";
    var client = clientFor("admin", "secret");
    var ex = assertThrows(HsdsClient.HsdsException.class, () -> client.createDomain("/shepard/abc/"));
    assertTrue(ex.getMessage().contains("HTTP 500"));
    assertTrue(ex.getMessage().contains("internal boom"));
  }

  @Test
  void invalidDomainShapeRejectedClientSide() {
    var client = clientFor("admin", "secret");
    assertThrows(IllegalArgumentException.class, () -> client.createDomain("no-leading-slash"));
    assertThrows(IllegalArgumentException.class, () -> client.createDomain(""));
    assertThrows(IllegalArgumentException.class, () -> client.createDomain(null));
    assertThrows(IllegalArgumentException.class, () -> client.createDomain("/contains?question"));
    assertEquals(0, capturedMethods.size(), "no requests issued for invalid domains");
  }

  @Test
  void endpointAccessor() {
    var client = clientFor("admin", "secret");
    assertTrue(client.getEndpoint().startsWith("http://127.0.0.1:"));
  }
}
