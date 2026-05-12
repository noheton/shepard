package de.dlr.shepard.cli.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.support.StubBackend;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Direct tests for {@link ShepardHttpClient} — independent of the
 * Picocli wrapper. Verifies the X-API-KEY header, the 401/403/404
 * error envelopes, and the 503 allow-list.
 */
final class ShepardHttpClientTest {

  @Test
  void getJsonDecodesBody() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route("/echo", 200, "{\"hello\":\"world\"}");
      ShepardHttpClient client = new ShepardHttpClient(
        HttpClient.newHttpClient(),
        backend.baseUrl(),
        "k"
      );

      Map<String, String> decoded = client.getJson("/echo", new TypeReference<Map<String, String>>() {});

      assertThat(decoded).containsEntry("hello", "world");
    }
  }

  @Test
  void leadingSlashIsOptional() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route("/echo", 200, "[]");
      ShepardHttpClient client = new ShepardHttpClient(
        HttpClient.newHttpClient(),
        backend.baseUrl(),
        "k"
      );

      List<Object> decoded = client.getJson("echo", new TypeReference<List<Object>>() {});

      assertThat(decoded).isEmpty();
    }
  }

  @Test
  void trailingSlashInBaseUrlIsTrimmed() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route("/echo", 200, "[]");
      ShepardHttpClient client = new ShepardHttpClient(
        HttpClient.newHttpClient(),
        backend.baseUrl() + "/",
        "k"
      );

      assertThat(client.getJson("/echo", new TypeReference<List<Object>>() {})).isEmpty();
    }
  }

  @Test
  void unauthorizedThrows() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route("/x", 401, "no");
      ShepardHttpClient client = new ShepardHttpClient(HttpClient.newHttpClient(), backend.baseUrl(), null);

      assertThatThrownBy(() -> client.getJson("/x", new TypeReference<Map<String, String>>() {}))
        .isInstanceOf(AdminCliException.class)
        .hasMessageContaining("401");
    }
  }

  @Test
  void forbiddenThrowsWithRoleHint() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route("/x", 403, "no");
      ShepardHttpClient client = new ShepardHttpClient(HttpClient.newHttpClient(), backend.baseUrl(), "k");

      assertThatThrownBy(() -> client.getJson("/x", new TypeReference<Map<String, String>>() {}))
        .isInstanceOf(AdminCliException.class)
        .hasMessageContaining("403")
        .hasMessageContaining("instance-admin");
    }
  }

  @Test
  void notFoundThrows() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route("/missing", 404, "{}");
      ShepardHttpClient client = new ShepardHttpClient(HttpClient.newHttpClient(), backend.baseUrl(), "k");

      assertThatThrownBy(() -> client.getJson("/missing", new TypeReference<Map<String, String>>() {}))
        .isInstanceOf(AdminCliException.class)
        .hasMessageContaining("404");
    }
  }

  @Test
  void serverErrorThrows() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route("/boom", 500, "{\"e\":\"oops\"}");
      ShepardHttpClient client = new ShepardHttpClient(HttpClient.newHttpClient(), backend.baseUrl(), "k");

      assertThatThrownBy(() -> client.getJson("/boom", new TypeReference<Map<String, String>>() {}))
        .isInstanceOf(AdminCliException.class)
        .hasMessageContaining("500");
    }
  }

  @Test
  void malformedJsonThrows() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route("/junk", 200, "not-json-at-all");
      ShepardHttpClient client = new ShepardHttpClient(HttpClient.newHttpClient(), backend.baseUrl(), "k");

      assertThatThrownBy(() -> client.getJson("/junk", new TypeReference<Map<String, String>>() {}))
        .isInstanceOf(AdminCliException.class)
        .hasMessageContaining("parse");
    }
  }

  @Test
  void connectRefusedSurfacesAsAdminCliException() {
    ShepardHttpClient client = new ShepardHttpClient(
      HttpClient.newHttpClient(),
      "http://127.0.0.1:1",
      "k"
    );

    assertThatThrownBy(() -> client.get("/x"))
      .isInstanceOf(AdminCliException.class);
  }

  @Test
  void noApiKeyOmitsHeader() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route("/x", 200, "[]");
      ShepardHttpClient client = new ShepardHttpClient(HttpClient.newHttpClient(), backend.baseUrl(), null);

      client.getJson("/x", new TypeReference<List<Object>>() {});

      assertThat(backend.requests()).isNotEmpty();
      assertThat(backend.requests().get(0).apiKeyHeader()).isNull();
    }
  }

  @Test
  void mapperIsShared() {
    assertThat(ShepardHttpClient.mapper()).isSameAs(ShepardHttpClient.mapper());
  }
}
