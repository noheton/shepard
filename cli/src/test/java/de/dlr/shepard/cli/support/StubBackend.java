package de.dlr.shepard.cli.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Tiny in-process HTTP server used by the CLI tests to stand in for
 * a running shepard backend. Each route is registered with a body
 * builder + status code; recorded requests are exposed so tests can
 * assert headers (e.g. {@code X-API-KEY}) and paths.
 *
 * <p>Implementation note: {@link com.sun.net.httpserver.HttpServer}
 * is part of the JDK ({@code jdk.httpserver} module). Picking it
 * avoids pulling in WireMock / MockWebServer / a Quarkus test harness
 * just for the CLI module, which keeps the dependency budget tight.
 */
public final class StubBackend implements AutoCloseable {

  private final HttpServer server;
  private final List<RecordedRequest> received = new ArrayList<>();

  private StubBackend(HttpServer server) {
    this.server = server;
  }

  public static StubBackend start() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
    return new StubBackend(server);
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  /** Register a JSON route — status 200, body produced by {@code bodyFn}. */
  public StubBackend route(String path, Function<RecordedRequest, String> bodyFn) {
    return route(path, 200, bodyFn);
  }

  /** Register a route with explicit status code. */
  public StubBackend route(String path, int status, Function<RecordedRequest, String> bodyFn) {
    server.createContext(path, exchange -> {
      RecordedRequest rr = new RecordedRequest(
        exchange.getRequestMethod(),
        exchange.getRequestURI().getPath(),
        exchange.getRequestHeaders().getFirst("X-API-KEY")
      );
      received.add(rr);
      String body;
      try {
        body = bodyFn.apply(rr);
      } catch (RuntimeException e) {
        exchange.sendResponseHeaders(500, 0);
        exchange.close();
        return;
      }
      byte[] payload = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, payload.length);
      exchange.getResponseBody().write(payload);
      exchange.close();
    });
    return this;
  }

  /** Register a route that captures a fixed status + body without inspecting the request. */
  public StubBackend route(String path, int status, String body) {
    AtomicReference<String> ref = new AtomicReference<>(body);
    return route(path, status, rr -> ref.get());
  }

  public List<RecordedRequest> requests() {
    return List.copyOf(received);
  }

  @Override
  public void close() {
    server.stop(0);
  }

  /** Lightweight value object — captures the parts of the request the CLI tests care about. */
  public record RecordedRequest(String method, String path, String apiKeyHeader) {}
}
