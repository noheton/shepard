package de.dlr.shepard.plugins.references.dbpediadatabus.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusPreviewIO;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * REF1c — unit tests for {@link DatabusHttpClient}: JSON-LD parsing,
 * auth header, connection-test, timeout constants.
 */
class DatabusHttpClientTest {

  private DatabusHttpClient client;
  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    // Use a real ObjectMapper but mock the HttpClient for network calls.
    HttpClient httpClient = mock(HttpClient.class);
    client = new DatabusHttpClient(httpClient, mapper);
  }

  // ─── parseJsonLdInto ─────────────────────────────────────────────────────

  @Test
  void parseJsonLdInto_flatObject_extractsTitle() throws IOException {
    String json = "{\"dct:title\":\"My Artifact\",\"dct:description\":\"About it\"}";
    DbpediaDatabusPreviewIO out = new DbpediaDatabusPreviewIO();
    client.parseJsonLdInto(json, out);
    assertThat(out.getTitle()).isEqualTo("My Artifact");
    assertThat(out.getDescription()).isEqualTo("About it");
  }

  @Test
  void parseJsonLdInto_graphArray_picksArtifactNode() throws IOException {
    String json =
      "{\"@graph\":[{\"@type\":\"dataid:Artifact\",\"dct:title\":\"ArtifactTitle\"," +
      "\"dct:description\":\"ArtifactDesc\"}]}";
    DbpediaDatabusPreviewIO out = new DbpediaDatabusPreviewIO();
    client.parseJsonLdInto(json, out);
    assertThat(out.getTitle()).isEqualTo("ArtifactTitle");
    assertThat(out.getDescription()).isEqualTo("ArtifactDesc");
  }

  @Test
  void parseJsonLdInto_arrayRoot_picksFirst() throws IOException {
    String json = "[{\"dct:title\":\"First\"},{\"dct:title\":\"Second\"}]";
    DbpediaDatabusPreviewIO out = new DbpediaDatabusPreviewIO();
    client.parseJsonLdInto(json, out);
    assertThat(out.getTitle()).isEqualTo("First");
  }

  @Test
  void parseJsonLdInto_valueObject_extractsAtValue() throws IOException {
    String json = "{\"dct:title\":{\"@value\":\"From @value\",\"@language\":\"en\"}}";
    DbpediaDatabusPreviewIO out = new DbpediaDatabusPreviewIO();
    client.parseJsonLdInto(json, out);
    assertThat(out.getTitle()).isEqualTo("From @value");
  }

  @Test
  void parseJsonLdInto_versionAndLicence_extracted() throws IOException {
    String json = "{\"dct:title\":\"T\",\"dcat:version\":\"2024.01.01\",\"dct:license\":\"CC0-1.0\"}";
    DbpediaDatabusPreviewIO out = new DbpediaDatabusPreviewIO();
    client.parseJsonLdInto(json, out);
    assertThat(out.getVersion()).isEqualTo("2024.01.01");
    assertThat(out.getLicence()).isEqualTo("CC0-1.0");
  }

  @Test
  void parseJsonLdInto_modifiedAt_parsedFromIso8601() throws IOException {
    String json = "{\"dct:modified\":\"2024-01-15T12:00:00Z\"}";
    DbpediaDatabusPreviewIO out = new DbpediaDatabusPreviewIO();
    client.parseJsonLdInto(json, out);
    assertThat(out.getModifiedAt()).isNotNull();
  }

  @Test
  void parseJsonLdInto_modifiedAt_dateOnlyFallback() throws IOException {
    String json = "{\"dct:modified\":\"2024-01-15\"}";
    DbpediaDatabusPreviewIO out = new DbpediaDatabusPreviewIO();
    client.parseJsonLdInto(json, out);
    assertThat(out.getModifiedAt()).isNotNull();
  }

  @Test
  void parseJsonLdInto_emptyBody_noException() throws IOException {
    String json = "{}";
    DbpediaDatabusPreviewIO out = new DbpediaDatabusPreviewIO();
    client.parseJsonLdInto(json, out);
    // no assertions — just must not throw
  }

  @Test
  void parseJsonLdInto_emptyArray_noException() throws IOException {
    String json = "[]";
    DbpediaDatabusPreviewIO out = new DbpediaDatabusPreviewIO();
    client.parseJsonLdInto(json, out);
    assertThat(out.getTitle()).isNull();
  }

  @Test
  void parseJsonLdInto_distributions_parsed() throws IOException {
    String json =
      "{\"dct:title\":\"T\",\"dcat:distribution\":[{\"dct:title\":\"file.ttl\"," +
      "\"dcat:mediaType\":\"text/turtle\",\"dcat:downloadURL\":\"https://example.org/file.ttl\"," +
      "\"dcat:byteSize\":12345}]}";
    DbpediaDatabusPreviewIO out = new DbpediaDatabusPreviewIO();
    client.parseJsonLdInto(json, out);
    assertThat(out.getDistributions()).hasSize(1);
    DbpediaDatabusPreviewIO.Distribution d = out.getDistributions().get(0);
    assertThat(d.getName()).isEqualTo("file.ttl");
    assertThat(d.getMimeType()).isEqualTo("text/turtle");
    assertThat(d.getDownloadUrl()).isEqualTo("https://example.org/file.ttl");
    assertThat(d.getByteSize()).isEqualTo(12345L);
  }

  // ─── fetchArtifact — null/blank uri guard ─────────────────────────────────

  @Test
  void fetchArtifact_nullUri_returnsUnavailable() {
    DbpediaDatabusPreviewIO out = client.fetchArtifact(null, DatabusHttpClient.AuthMode.none());
    assertThat(out.isAvailable()).isFalse();
    assertThat(out.getReason()).isEqualTo("invalid-uri");
  }

  @Test
  void fetchArtifact_blankUri_returnsUnavailable() {
    DbpediaDatabusPreviewIO out = client.fetchArtifact("  ", DatabusHttpClient.AuthMode.none());
    assertThat(out.isAvailable()).isFalse();
    assertThat(out.getReason()).isEqualTo("invalid-uri");
  }

  // ─── timeout constants ────────────────────────────────────────────────────

  @Test
  void timeoutConstants_areReasonable() {
    assertThat(DatabusHttpClient.CONNECT_TIMEOUT.getSeconds()).isEqualTo(10L);
    assertThat(DatabusHttpClient.REQUEST_TIMEOUT.getSeconds()).isEqualTo(30L);
    assertThat(DatabusHttpClient.RETRY_BACKOFF.getSeconds()).isEqualTo(1L);
  }

  // ─── AuthMode factory ────────────────────────────────────────────────────

  @Test
  void authMode_none_notOauth() {
    DatabusHttpClient.AuthMode auth = DatabusHttpClient.AuthMode.none();
    assertThat(auth.isOauthClientCredentials()).isFalse();
  }

  @Test
  void authMode_oauthClientCredentials_isOauth() {
    DatabusHttpClient.AuthMode auth = DatabusHttpClient.AuthMode.oauthClientCredentials("url", "id", "secret");
    assertThat(auth.isOauthClientCredentials()).isTrue();
  }

  // ─── ConnectionTestResult record ──────────────────────────────────────────

  @Test
  void connectionTestResult_record_accessors() {
    DatabusHttpClient.ConnectionTestResult r = new DatabusHttpClient.ConnectionTestResult(true, 200, 42L, null);
    assertThat(r.reachable()).isTrue();
    assertThat(r.statusCode()).isEqualTo(200);
    assertThat(r.latencyMs()).isEqualTo(42L);
    assertThat(r.reason()).isNull();
  }

  // ─── clearTokenCache ─────────────────────────────────────────────────────

  @Test
  void clearTokenCache_doesNotThrow() {
    client.clearTokenCache();
    client.clearTokenCache(); // idempotent
  }
}
