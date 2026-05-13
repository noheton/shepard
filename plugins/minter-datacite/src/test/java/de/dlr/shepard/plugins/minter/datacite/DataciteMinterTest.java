package de.dlr.shepard.plugins.minter.datacite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.plugins.minter.datacite.daos.DataciteHttpClient;
import de.dlr.shepard.plugins.minter.datacite.daos.DataciteHttpClient.DataciteHttpResponse;
import de.dlr.shepard.plugins.minter.datacite.entities.DataciteMinterConfig;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService;
import de.dlr.shepard.publish.minter.MintRequest;
import de.dlr.shepard.publish.minter.MintResult;
import de.dlr.shepard.publish.minter.MinterException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * KIP1d — DataciteMinter unit tests.
 *
 * <p>Mocks {@link DataciteHttpClient} (the HTTP boundary) and
 * {@link DataciteMinterConfigService} (the Neo4j boundary) so we
 * exercise the JSON request-builder, the retry hook, the
 * version-segment handling, and the back-fill flow in isolation.
 */
class DataciteMinterTest {

  private DataciteMinterConfigService configService;
  private DataciteHttpClient http;
  private DataciteMinter minter;

  private static final String SUCCESS_BODY = """
      { "data": { "id": "10.5072/abc-def-1", "type": "dois",
        "attributes": { "doi": "10.5072/abc-def-1" } } }
      """;

  @BeforeEach
  void setUp() {
    configService = mock(DataciteMinterConfigService.class);
    http = mock(DataciteHttpClient.class);
    minter = new DataciteMinter();
    minter.configService = configService;
    minter.http = http;
  }

  private DataciteMinterConfig fullyConfiguredCfg() {
    DataciteMinterConfig cfg = new DataciteMinterConfig();
    cfg.setEnabled(true);
    cfg.setApiBaseUrl("https://api.test.datacite.org");
    cfg.setHandlePrefix("10.5072");
    cfg.setRepositoryId("DLR.SHEPARD");
    cfg.setPasswordCipher("gcm1:fake-cipher");
    cfg.setPasswordHash("a".repeat(64));
    cfg.setPublisher("DLR");
    cfg.setLandingPageBase("https://shepard.example.org/v2");
    cfg.setDefaultState("draft");
    return cfg;
  }

  // ─── id / isEnabled ─────────────────────────────────────────────────────

  @Test
  void id_isStable() {
    assertThat(minter.id()).isEqualTo("datacite");
    assertThat(DataciteMinter.ID).isEqualTo("datacite");
  }

  @Test
  void isEnabled_falseWhenDisabledOrMissingFields() {
    DataciteMinterConfig disabled = fullyConfiguredCfg();
    disabled.setEnabled(false);
    when(configService.current()).thenReturn(disabled);
    assertThat(minter.isEnabled()).isFalse();

    DataciteMinterConfig noPrefix = fullyConfiguredCfg();
    noPrefix.setHandlePrefix(null);
    when(configService.current()).thenReturn(noPrefix);
    assertThat(minter.isEnabled()).isFalse();

    DataciteMinterConfig noCred = fullyConfiguredCfg();
    noCred.setPasswordCipher(null);
    when(configService.current()).thenReturn(noCred);
    assertThat(minter.isEnabled()).isFalse();
  }

  @Test
  void isEnabled_trueWhenFullyConfigured() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    assertThat(minter.isEnabled()).isTrue();
  }

  @Test
  void isEnabled_falseOnRuntimeException() {
    when(configService.current()).thenThrow(new RuntimeException("boom"));
    assertThat(minter.isEnabled()).isFalse();
  }

  // ─── mint happy path ────────────────────────────────────────────────────

  @Test
  void mint_returnsDoiFromDataciteResponse() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("the-password");
    when(http.post(anyString(), anyString(), anyString())).thenReturn(new DataciteHttpResponse(201, SUCCESS_BODY));

    MintRequest req = new MintRequest(
      "data-objects",
      "01950000-aaaa-bbbb-cccc-000000000001",
      "https://shepard.example.org/v2/data-objects/01950000-aaaa-bbbb-cccc-000000000001",
      1,
      Map.of("name", "First publication")
    );

    MintResult result = minter.mint(req);

    assertThat(result.pid()).isEqualTo("10.5072/abc-def-1");
    assertThat(result.minterId()).isEqualTo("datacite");
    assertThat(result.mintedAt()).isNotNull();

    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(http).post(eq("https://api.test.datacite.org/dois"), bodyCaptor.capture(), anyString());

    String body = bodyCaptor.getValue();
    assertThat(body).contains("\"prefix\":\"10.5072\"");
    assertThat(body).contains("\"version\":\"v1\"");
    assertThat(body).contains("\"event\":\"draft\"");
    assertThat(body).contains("\"publisher\":\"DLR\"");
  }

  @Test
  void mint_throwsWhenDisabled() {
    DataciteMinterConfig cfg = fullyConfiguredCfg();
    cfg.setEnabled(false);
    when(configService.current()).thenReturn(cfg);

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    assertThatThrownBy(() -> minter.mint(req))
      .isInstanceOf(MinterException.class)
      .hasMessageContaining("disabled");

    verify(http, never()).post(anyString(), anyString(), anyString());
  }

  @Test
  void mint_throwsWhenHandlePrefixUnset() {
    DataciteMinterConfig cfg = fullyConfiguredCfg();
    cfg.setHandlePrefix(null);
    when(configService.current()).thenReturn(cfg);

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    assertThatThrownBy(() -> minter.mint(req))
      .isInstanceOf(MinterException.class)
      .hasMessageContaining("handlePrefix");
  }

  @Test
  void mint_throwsOnNon201Response() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("pwd");
    when(http.post(anyString(), anyString(), anyString())).thenReturn(new DataciteHttpResponse(403, "{\"errors\":[\"bad auth\"]}"));

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    assertThatThrownBy(() -> minter.mint(req))
      .isInstanceOf(MinterException.class)
      .hasMessageContaining("HTTP 403");
  }

  @Test
  void mint_throwsOnUnparseableResponseBody() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("pwd");
    when(http.post(anyString(), anyString(), anyString()))
      .thenReturn(new DataciteHttpResponse(201, "this is not json"));

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    assertThatThrownBy(() -> minter.mint(req))
      .isInstanceOf(MinterException.class)
      .hasMessageContaining("parse");
  }

  // ─── retry behaviour ────────────────────────────────────────────────────

  @Test
  void mint_retriesOnce_on5xx() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("pwd");
    when(http.post(anyString(), anyString(), anyString()))
      .thenReturn(new DataciteHttpResponse(503, "service unavailable"))
      .thenReturn(new DataciteHttpResponse(201, SUCCESS_BODY));

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    MintResult result = minter.mint(req);

    assertThat(result.pid()).isEqualTo("10.5072/abc-def-1");
    verify(http, times(2)).post(anyString(), anyString(), anyString());
  }

  @Test
  void mint_retriesOnNetworkExceptionThenSucceeds() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("pwd");
    when(http.post(anyString(), anyString(), anyString()))
      .thenThrow(new RuntimeException("transient network error"))
      .thenReturn(new DataciteHttpResponse(201, SUCCESS_BODY));

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    MintResult result = minter.mint(req);

    assertThat(result.pid()).isEqualTo("10.5072/abc-def-1");
    verify(http, times(2)).post(anyString(), anyString(), anyString());
  }

  @Test
  void mint_doesNotRetryOn4xx() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("pwd");
    when(http.post(anyString(), anyString(), anyString())).thenReturn(new DataciteHttpResponse(400, "bad"));

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    assertThatThrownBy(() -> minter.mint(req))
      .isInstanceOf(MinterException.class)
      .hasMessageContaining("HTTP 400");

    verify(http, times(1)).post(anyString(), anyString(), anyString());
  }

  @Test
  void mint_failsAfterSecondNetworkException() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("pwd");
    when(http.post(anyString(), anyString(), anyString()))
      .thenThrow(new RuntimeException("error 1"))
      .thenThrow(new RuntimeException("error 2"));

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", Map.of());

    assertThatThrownBy(() -> minter.mint(req))
      .isInstanceOf(MinterException.class)
      .hasMessageContaining("retry");

    verify(http, times(2)).post(anyString(), anyString(), anyString());
  }

  // ─── versioning ─────────────────────────────────────────────────────────

  @Test
  void mint_encodesVersionNumberAsLiteralVN() throws Exception {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("pwd");
    when(http.post(anyString(), anyString(), anyString())).thenReturn(new DataciteHttpResponse(201, SUCCESS_BODY));

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", 3, Map.of());

    minter.mint(req);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(http).post(anyString(), body.capture(), anyString());
    JsonNode tree = new ObjectMapper().readTree(body.getValue());
    assertThat(tree.path("data").path("attributes").path("version").asText()).isEqualTo("v3");
  }

  @Test
  void mint_addsIsNewVersionOfWhenVersionGreaterThanOne() throws Exception {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("pwd");
    when(http.post(anyString(), anyString(), anyString())).thenReturn(new DataciteHttpResponse(201, SUCCESS_BODY));
    when(http.put(anyString(), anyString(), anyString())).thenReturn(new DataciteHttpResponse(200, "{}"));

    minter.previousResolver((appId, n) -> "10.5072/abc-def-0");

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", 2, Map.of());

    minter.mint(req);

    ArgumentCaptor<String> postBody = ArgumentCaptor.forClass(String.class);
    verify(http).post(anyString(), postBody.capture(), anyString());
    JsonNode tree = new ObjectMapper().readTree(postBody.getValue());
    JsonNode relations = tree.path("data").path("attributes").path("relatedIdentifiers");
    assertThat(relations.isArray()).isTrue();
    assertThat(relations.get(0).path("relationType").asText()).isEqualTo("IsNewVersionOf");
    assertThat(relations.get(0).path("relatedIdentifier").asText()).isEqualTo("10.5072/abc-def-0");

    ArgumentCaptor<String> putUrl = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> putBody = ArgumentCaptor.forClass(String.class);
    verify(http).put(putUrl.capture(), putBody.capture(), anyString());
    assertThat(putUrl.getValue()).endsWith("/10.5072/abc-def-0");
    JsonNode put = new ObjectMapper().readTree(putBody.getValue());
    assertThat(put.path("data").path("attributes").path("relatedIdentifiers").get(0).path("relationType").asText())
      .isEqualTo("HasVersion");
  }

  @Test
  void mint_skipsBackfillWhenVersionOne() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("pwd");
    when(http.post(anyString(), anyString(), anyString())).thenReturn(new DataciteHttpResponse(201, SUCCESS_BODY));

    minter.previousResolver((appId, n) -> "should-not-be-called");

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", 1, Map.of());

    minter.mint(req);

    verify(http, never()).put(anyString(), anyString(), anyString());
  }

  @Test
  void mint_backfillFailureIsNonFatal() {
    when(configService.current()).thenReturn(fullyConfiguredCfg());
    when(configService.resolvePlaintext()).thenReturn("pwd");
    when(http.post(anyString(), anyString(), anyString())).thenReturn(new DataciteHttpResponse(201, SUCCESS_BODY));
    when(http.put(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("backfill broke"));

    minter.previousResolver((appId, n) -> "10.5072/abc-def-0");

    MintRequest req = new MintRequest("data-objects", "appid", "https://x/y", 2, Map.of());

    MintResult result = minter.mint(req);

    // mint still succeeded
    assertThat(result.pid()).isEqualTo("10.5072/abc-def-1");
    verify(http, atLeast(1)).put(anyString(), anyString(), anyString());
  }

  // ─── digital-object-type mapping ────────────────────────────────────────

  @Test
  void resourceTypeGeneral_mapsKnownKinds() {
    assertThat(DataciteMinter.mapToResourceTypeGeneral("dataset")).isEqualTo("Dataset");
    assertThat(DataciteMinter.mapToResourceTypeGeneral("collection")).isEqualTo("Collection");
    assertThat(DataciteMinter.mapToResourceTypeGeneral("Software")).isEqualTo("Software");
    assertThat(DataciteMinter.mapToResourceTypeGeneral("publication")).isEqualTo("Text");
    assertThat(DataciteMinter.mapToResourceTypeGeneral("image")).isEqualTo("Image");
  }

  @Test
  void resourceTypeGeneral_fallsBackToDataset() {
    assertThat(DataciteMinter.mapToResourceTypeGeneral(null)).isEqualTo("Dataset");
    assertThat(DataciteMinter.mapToResourceTypeGeneral("widget")).isEqualTo("Dataset");
  }

  @Test
  void eventFor_mapsStatesToDataciteEventNames() {
    assertThat(DataciteMinter.eventFor("draft")).isEqualTo("draft");
    assertThat(DataciteMinter.eventFor("registered")).isEqualTo("register");
    assertThat(DataciteMinter.eventFor("findable")).isEqualTo("publish");
    assertThat(DataciteMinter.eventFor(null)).isEqualTo("draft");
    assertThat(DataciteMinter.eventFor("unknown")).isEqualTo("draft");
  }

  // ─── basic auth header ──────────────────────────────────────────────────

  @Test
  void basicAuth_producesBasicHeader() {
    String header = DataciteMinter.basicAuth("user", "pwd");
    assertThat(header).startsWith("Basic ");
    // base64("user:pwd") = "dXNlcjpwd2Q="
    assertThat(header).isEqualTo("Basic dXNlcjpwd2Q=");
  }

  @Test
  void basicAuth_handlesNullPasswordGracefully() {
    String header = DataciteMinter.basicAuth("user", null);
    assertThat(header).startsWith("Basic ");
    // base64("user:") = "dXNlcjo="
    assertThat(header).isEqualTo("Basic dXNlcjo=");
  }

  // ─── version extraction ────────────────────────────────────────────────

  @Test
  void extractVersionNumber_readsMintRequestField() {
    MintRequest req = new MintRequest("data-objects", "a", "https://x/y", 7, Map.of());
    assertThat(DataciteMinter.extractVersionNumber(req)).isEqualTo(7);
  }

  @Test
  void extractVersionNumber_clampsToOneOnInvalidInput() {
    // Pre-KIP1h-compatible factory defaults to 1; clamp logic is still
    // a useful belt-and-braces here.
    MintRequest req = new MintRequest("data-objects", "a", "https://x/y", Map.of());
    assertThat(DataciteMinter.extractVersionNumber(req)).isEqualTo(1);
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  @Test
  void stripTrailingSlash_idempotent() {
    assertThat(DataciteMinter.stripTrailingSlash("https://x/")).isEqualTo("https://x");
    assertThat(DataciteMinter.stripTrailingSlash("https://x")).isEqualTo("https://x");
    assertThat(DataciteMinter.stripTrailingSlash(null)).isEqualTo("");
  }

  @Test
  void truncate_capsLongStrings() {
    assertThat(DataciteMinter.truncate("hello world", 5)).isEqualTo("hello" + "…");
    assertThat(DataciteMinter.truncate("short", 100)).isEqualTo("short");
    assertThat(DataciteMinter.truncate(null, 10)).isEqualTo("");
  }

  @Test
  void firstNonBlank_returnsFallbackWhenBlank() {
    assertThat(DataciteMinter.firstNonBlank(null, "fallback")).isEqualTo("fallback");
    assertThat(DataciteMinter.firstNonBlank("", "fallback")).isEqualTo("fallback");
    assertThat(DataciteMinter.firstNonBlank("   ", "fallback")).isEqualTo("fallback");
    assertThat(DataciteMinter.firstNonBlank("real", "fallback")).isEqualTo("real");
  }

  @Test
  void previousResolver_nullDefaultsToNoOp() {
    minter.previousResolver(null);
    assertThat(minter.currentPreviousResolver().previousPid("any", 5)).isNull();
  }

  @Test
  void buildRequestBody_usesEntityKindAndAppIdInUrlAndTitle() throws Exception {
    DataciteMinterConfig cfg = fullyConfiguredCfg();
    MintRequest req = new MintRequest("collections", "appid-42", "https://x/y", 1, Map.of());

    String body = minter.buildRequestBody(cfg, req, 1, null);
    JsonNode tree = new ObjectMapper().readTree(body);

    assertThat(tree.path("data").path("attributes").path("url").asText())
      .isEqualTo("https://shepard.example.org/v2/collections/appid-42");
    assertThat(tree.path("data").path("attributes").path("titles").get(0).path("title").asText())
      .isEqualTo("collections appid-42");
  }

  @Test
  void buildRequestBody_usesMetadataNameWhenPresent() throws Exception {
    DataciteMinterConfig cfg = fullyConfiguredCfg();
    MintRequest req = new MintRequest(
      "data-objects",
      "app",
      "https://x/y",
      1,
      Map.of("name", "Custom title", "rightsHolder", "Alice")
    );

    String body = minter.buildRequestBody(cfg, req, 1, null);
    JsonNode tree = new ObjectMapper().readTree(body);

    assertThat(tree.path("data").path("attributes").path("titles").get(0).path("title").asText())
      .isEqualTo("Custom title");
    assertThat(tree.path("data").path("attributes").path("creators").get(0).path("name").asText())
      .isEqualTo("Alice");
  }
}
