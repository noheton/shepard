package de.dlr.shepard.data.hdf.hsds;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A5d — coverage for {@link HsdsClient#exportFile}.
 *
 * <p>Uses the same loopback {@link HttpServer} stub shape as
 * {@link HsdsClientTest} to verify auth header, Accept header,
 * Range pass-through, 200 body streaming, and 206 Partial Content.
 */
class HsdsClientExportTest {

  private HttpServer server;
  private List<String> capturedMethods;
  private List<String> capturedUris;
  private List<String> capturedAccept;
  private List<String> capturedRange;
  private AtomicInteger nextStatus;
  private byte[] nextBody;
  private AtomicReference<String> nextContentRange;
  private AtomicReference<String> nextAcceptRanges;

  @BeforeEach
  void start() throws IOException {
    capturedMethods = new CopyOnWriteArrayList<>();
    capturedUris = new CopyOnWriteArrayList<>();
    capturedAccept = new CopyOnWriteArrayList<>();
    capturedRange = new CopyOnWriteArrayList<>();
    nextStatus = new AtomicInteger(200);
    nextBody = "FAKE_HDF5".getBytes(StandardCharsets.UTF_8);
    nextContentRange = new AtomicReference<>(null);
    nextAcceptRanges = new AtomicReference<>("bytes");

    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      capturedMethods.add(exchange.getRequestMethod());
      capturedUris.add(exchange.getRequestURI().toString());
      capturedAccept.add(exchange.getRequestHeaders().getFirst("Accept"));
      String rangeHdr = exchange.getRequestHeaders().getFirst("Range");
      capturedRange.add(rangeHdr); // null if absent
      try (var is = exchange.getRequestBody()) {
        is.readAllBytes();
      }
      byte[] body = nextBody;
      exchange.getResponseHeaders().set("Content-Type", "application/x-hdf5");
      if (nextAcceptRanges.get() != null) {
        exchange.getResponseHeaders().set("Accept-Ranges", nextAcceptRanges.get());
      }
      if (nextContentRange.get() != null) {
        exchange.getResponseHeaders().set("Content-Range", nextContentRange.get());
      }
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

  // ─── A5d: exportFile ────────────────────────────────────────────────────

  @Test
  void exportFileSuccess200SendsGetWithHdf5Accept() throws IOException {
    nextStatus.set(200);
    var client = clientFor("admin", "secret");

    try (var resp = client.exportFile("/shepard/abc/", null)) {
      assertEquals(200, resp.status());
      assertEquals("GET", capturedMethods.get(0));
      assertTrue(capturedUris.get(0).contains("domain=/shepard/abc/"), "domain param present");
      assertEquals("application/x-hdf5", capturedAccept.get(0), "Accept header set to HDF5");
      assertArrayEquals("FAKE_HDF5".getBytes(StandardCharsets.UTF_8), resp.body().readAllBytes());
    }
  }

  @Test
  void exportFilePassesRangeHeaderToHsds() throws IOException {
    nextStatus.set(206);
    nextContentRange.set("bytes 0-8/9");
    nextBody = "FAKE_HDF".getBytes(StandardCharsets.UTF_8);
    var client = clientFor("admin", "secret");

    try (var resp = client.exportFile("/shepard/abc/", "bytes=0-8")) {
      assertEquals(206, resp.status());
      assertEquals("bytes=0-8", capturedRange.get(0), "Range header forwarded");
      assertEquals("bytes 0-8/9", resp.contentRange(), "Content-Range forwarded");
    }
  }

  @Test
  void exportFileNoRangeHeaderNotSentToHsds() throws IOException {
    nextStatus.set(200);
    var client = clientFor("admin", "secret");

    try (var resp = client.exportFile("/shepard/abc/", null)) {
      assertEquals(200, resp.status());
      assertNull(capturedRange.get(0), "Range header must not be sent when absent");
    }
  }

  @Test
  void exportFile206PropagatesAcceptRanges() throws IOException {
    nextStatus.set(206);
    nextAcceptRanges.set("bytes");
    nextContentRange.set("bytes 0-3/9");
    var client = clientFor("admin", "secret");

    try (var resp = client.exportFile("/shepard/abc/", "bytes=0-3")) {
      assertEquals(206, resp.status());
      assertEquals("bytes", resp.acceptRanges());
      assertEquals("bytes 0-3/9", resp.contentRange());
      assertNotNull(resp.body());
    }
  }

  @Test
  void exportFileUnauthorizedSurfacesOperatorMessage() {
    nextStatus.set(401);
    nextBody = new byte[0];
    var client = clientFor("admin", "wrong");

    var ex = assertThrows(HsdsClient.HsdsException.class, () -> client.exportFile("/shepard/abc/", null));
    assertTrue(
      ex.getMessage().contains("Verify shepard.hdf.hsds.username"),
      "operator-readable hint missing: " + ex.getMessage()
    );
  }

  @Test
  void exportFileHsdsErrorSurfacesStatusCode() {
    nextStatus.set(500);
    nextBody = new byte[0];
    var client = clientFor("admin", "secret");

    var ex = assertThrows(HsdsClient.HsdsException.class, () -> client.exportFile("/shepard/abc/", null));
    assertTrue(ex.getMessage().contains("HTTP 500"), "status code in message: " + ex.getMessage());
  }

  @Test
  void exportFileInvalidDomainRejectedClientSide() {
    var client = clientFor("admin", "secret");
    assertThrows(IllegalArgumentException.class, () -> client.exportFile("no-leading-slash", null));
    assertThrows(IllegalArgumentException.class, () -> client.exportFile(null, null));
    assertEquals(0, capturedMethods.size(), "no requests issued for invalid domains");
  }
}
